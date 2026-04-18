package com.dao;

import com.conn.DBConnect;
import com.entity.Ad;
import com.entity.ExchangeAd;
import com.entity.GiftAd;
import com.entity.SaleAd;
import com.entity.Category;
import java.sql.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class AdDAO {
    private Connection conn;

    public AdDAO(Connection conn) {
        this.conn = conn;
    }

    /**
     * Crea un nuovo annuncio richiamando la stored procedure sp_create_ad.
     * La procedura risolve internamente la categoria tramite fn_get_category_id.
     * Dopo la chiamata, inserisce le immagini aggiuntive tramite sp_add_ad_image.
     */
    public boolean createAd(Ad ad) {
        try {
            CallableStatement cs = conn.prepareCall("{CALL sp_create_ad(?,?,?,?,?,?,?,?,?,?)}");
            cs.setLong(1, ad.getUserId());
            cs.setString(2, ad.getTitle());
            cs.setString(3, ad.getDescription());
            cs.setString(4, ad.getType());
            cs.setString(5, ad.getCategory().getName());
            cs.setBigDecimal(6, ad.getPrice());
            cs.setString(7, ad.getLocation());
            cs.setString(8, ad.getPickupTime());
            // Logica DAO: se l'annuncio non ha immagini, il DAO assegna
            // autonomamente l'immagine di default prima di persistere.
            String mainImage = resolveMainImage(ad);
            cs.setString(9, mainImage);
            cs.registerOutParameter(10, Types.BIGINT);
            cs.execute();

            long adId = cs.getLong(10);
            ad.setId(adId);

            // Inserisce le immagini aggiuntive tramite sp_add_ad_image
            if (ad.getImageUrls() != null && !ad.getImageUrls().isEmpty()) {
                CallableStatement csImg = conn.prepareCall("{CALL sp_add_ad_image(?,?)}");
                for (String url : ad.getImageUrls()) {
                    csImg.setLong(1, adId);
                    csImg.setString(2, url);
                    csImg.addBatch();
                }
                csImg.executeBatch();
            }
            return adId > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Aggiorna un annuncio esistente tramite sp_update_ad.
     * Gestisce il rimpiazzo delle immagini con sp_replace_ad_images + sp_add_ad_image.
     */
    public boolean updateAd(Ad ad) {
        try {
            CallableStatement cs = conn.prepareCall("{CALL sp_update_ad(?,?,?,?,?,?,?,?,?,?)}");
            cs.setLong(1, ad.getId());
            cs.setString(2, ad.getTitle());
            cs.setString(3, ad.getDescription());
            cs.setString(4, ad.getType());
            cs.setString(5, ad.getCategory().getName());
            cs.setBigDecimal(6, ad.getPrice());
            cs.setString(7, ad.getLocation());
            cs.setString(8, ad.getPickupTime());
            String mainImage = (ad.getImageUrls() != null && !ad.getImageUrls().isEmpty())
                    ? ad.getImageUrls().get(0) : ad.getImageUrl();
            cs.setString(9, mainImage);
            cs.setString(10, ad.getStatus());
            cs.execute();

            // Aggiorna le immagini: DELETE tramite sp_replace_ad_images, poi re-INSERT
            if (ad.getImageUrls() != null && !ad.getImageUrls().isEmpty()) {
                CallableStatement csDel = conn.prepareCall("{CALL sp_replace_ad_images(?)}");
                csDel.setLong(1, ad.getId());
                csDel.execute();

                CallableStatement csImg = conn.prepareCall("{CALL sp_add_ad_image(?,?)}");
                for (String url : ad.getImageUrls()) {
                    csImg.setLong(1, ad.getId());
                    csImg.setString(2, url);
                    csImg.addBatch();
                }
                csImg.executeBatch();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Elimina un annuncio tramite sp_delete_ad.
     * Il CASCADE sulle FK elimina automaticamente ad_images e offerte correlate.
     */
    public boolean deleteAd(Long id) {
        try {
            CallableStatement cs = conn.prepareCall("{CALL sp_delete_ad(?)}");
            cs.setLong(1, id);
            cs.execute();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Ritorna tutti gli annunci attivi ordinati per data di creazione decrescente.
     * Le operazioni di SELECT pura rimangono nel DAO come query dirette
     * poiché sono solo letture senza logica di business.
     */
    public List<Ad> getAllAds() {
        List<Ad> ads = new ArrayList<>();
        try {
            String sql = "SELECT a.*, c.name as category_name FROM ads a LEFT JOIN categories c ON a.category_id = c.id ORDER BY a.created_at DESC";
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ads.add(createAdFromResultSet(rs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ads;
    }

    /** Ritorna tutti gli annunci di un utente specifico. */
    public List<Ad> getAdsByUserId(Long userId) {
        List<Ad> list = new ArrayList<>();
        try {
            String sql = "SELECT a.*, c.name as category_name FROM ads a LEFT JOIN categories c ON a.category_id = c.id WHERE a.user_id = ? ORDER BY a.created_at DESC";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(createAdFromResultSet(rs));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Ritorna un singolo annuncio per ID, incluse le immagini aggiuntive.
     */
    public Ad getAdById(Long id) {
        Ad ad = null;
        try {
            String sql = "SELECT a.*, c.name as category_name FROM ads a LEFT JOIN categories c ON a.category_id = c.id WHERE a.id = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ad = createAdFromResultSet(rs);
                ad.setImageUrls(getAdImages(ad.getId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ad;
    }

    /** Ritorna gli annunci attivi filtrati per categoria. */
    public List<Ad> getAdsByCategory(String categoryName) {
        List<Ad> ads = new ArrayList<>();
        try {
            String sql = "SELECT a.*, c.name as category_name FROM ads a LEFT JOIN categories c ON a.category_id = c.id WHERE c.name = ? AND a.status = 'ACTIVE' ORDER BY a.created_at DESC";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, categoryName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) ads.add(createAdFromResultSet(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ads;
    }

    /** Ritorna gli annunci attivi filtrati per tipo (SALE, EXCHANGE, GIFT). */
    public List<Ad> getAdsByType(String type) {
        List<Ad> ads = new ArrayList<>();
        try {
            String sql = "SELECT a.*, c.name as category_name FROM ads a LEFT JOIN categories c ON a.category_id = c.id WHERE a.type = ? AND a.status = 'ACTIVE' ORDER BY a.created_at DESC";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, type);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) ads.add(createAdFromResultSet(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ads;
    }

    /**
     * Ricerca filtrata per categoria, tipo e fascia di prezzo.
     * La query viene costruita dinamicamente aggiungendo clausole WHERE
     * solo per i filtri non nulli.
     */
    public List<Ad> getAdsByFilters(String category, String type, BigDecimal minPrice, BigDecimal maxPrice) {
        List<Ad> ads = new ArrayList<>();
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT a.*, c.name as category_name FROM ads a LEFT JOIN categories c ON a.category_id = c.id WHERE a.status = 'ACTIVE'");
            List<Object> params = new ArrayList<>();

            if (category != null && !category.trim().isEmpty()) { sql.append(" AND c.name = ?");    params.add(category); }
            if (type     != null && !type.trim().isEmpty())     { sql.append(" AND a.type = ?");    params.add(type); }
            if (minPrice != null)                               { sql.append(" AND a.price >= ?");  params.add(minPrice); }
            if (maxPrice != null)                               { sql.append(" AND a.price <= ?");  params.add(maxPrice); }

            sql.append(" ORDER BY a.created_at DESC");
            PreparedStatement ps = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) ads.add(createAdFromResultSet(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ads;
    }

    // ── Metodi privati di supporto ──────────────────────────────────────────

    /**
     * Logica DAO: determina l'immagine principale dell'annuncio.
     * Se la lista di immagini è presente usa la prima; se è presente
     * solo image_url singola usa quella; altrimenti assegna il placeholder
     * "default_ad.png" in modo che il record non rimanga mai privo di immagine.
     * Questa scelta non appartiene né al database né al livello di presentazione:
     * è una regola di coerenza del dato gestita nel layer di accesso ai dati.
     */
    private String resolveMainImage(Ad ad) {
        if (ad.getImageUrls() != null && !ad.getImageUrls().isEmpty()) {
            return ad.getImageUrls().get(0);
        }
        if (ad.getImageUrl() != null && !ad.getImageUrl().isEmpty()) {
            return ad.getImageUrl();
        }
        return "default_ad.png";
    }

    /** Carica le immagini aggiuntive di un annuncio dalla tabella ad_images. */
    private List<String> getAdImages(long adId) {
        List<String> images = new ArrayList<>();
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT image_url FROM ad_images WHERE ad_id = ?");
            ps.setLong(1, adId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) images.add(rs.getString("image_url"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return images;
    }

    /**
     * Costruisce l'oggetto Ad (con la sottoclasse corretta) dal ResultSet.
     * Usa il campo discriminatore 'type' per istanziare SaleAd, ExchangeAd o GiftAd.
     */
    private Ad createAdFromResultSet(ResultSet rs) throws SQLException {
        String type = rs.getString("type");
        Ad ad;
        if ("SALE".equalsIgnoreCase(type))          ad = new SaleAd();
        else if ("EXCHANGE".equalsIgnoreCase(type)) ad = new ExchangeAd();
        else                                        ad = new GiftAd();

        ad.setId(rs.getLong("id"));
        ad.setUserId(rs.getLong("user_id"));
        ad.setTitle(rs.getString("title"));
        ad.setDescription(rs.getString("description"));
        ad.setType(type);

        Category category = new Category();
        String categoryName = rs.getString("category_name");
        category.setName(categoryName != null ? categoryName : "");
        ad.setCategory(category);

        ad.setPrice(rs.getBigDecimal("price"));
        ad.setLocation(rs.getString("location"));
        ad.setPickupTime(rs.getString("pickup_time"));
        ad.setImageUrl(rs.getString("image_url"));
        ad.setStatus(rs.getString("status"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) ad.setCreatedAt(createdAt.toLocalDateTime());
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) ad.setUpdatedAt(updatedAt.toLocalDateTime());

        return ad;
    }
}
