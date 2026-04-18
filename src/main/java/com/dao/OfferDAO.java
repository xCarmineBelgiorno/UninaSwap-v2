package com.dao;

import com.entity.ExchangeBid;
import com.entity.GiftBid;
import com.entity.Offer;
import com.entity.SaleBid;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OfferDAO {
    private Connection conn;

    public OfferDAO(Connection conn) {
        this.conn = conn;
    }

    /**
     * Verifica se l'utente ha già un'offerta PENDING per un dato annuncio.
     * Chiama la funzione SQL fn_has_pending_offer, delegando al database
     * la logica di conteggio e verifica.
     */
    public boolean hasPendingOffer(Long adId, Long userId) {
        try {
            String sql = "SELECT fn_has_pending_offer(?, ?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setLong(1, adId);
            ps.setLong(2, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBoolean(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Crea una nuova offerta richiamando la stored procedure sp_create_offer.
     * I trigger trg_offers_before_insert e trg_offers_no_self_bid vengono
     * eseguiti automaticamente dal database prima dell'INSERT, garantendo
     * che l'annuncio sia attivo e che l'offerente non sia il proprietario.
     * Dopo la creazione, invia la notifica al proprietario tramite sp_create_notification.
     */
    public boolean createOffer(Offer offer) {
        try {
            // Logica DAO: se la descrizione non è stata fornita, ne genera
            // una di default leggibile basata sul tipo dell'offerta e sul prezzo.
            // Questa normalizzazione avviene prima della chiamata al DB.
            if (offer.getDescription() == null || offer.getDescription().trim().isEmpty()) {
                offer.setDescription(buildDefaultDescription(offer));
            }

            CallableStatement cs = conn.prepareCall("{CALL sp_create_offer(?,?,?,?,?,?)}");
            cs.setLong(1, offer.getAdId());
            cs.setLong(2, offer.getUserId());
            cs.setString(3, offer.getType());
            cs.setBigDecimal(4, offer.getPrice());
            cs.setString(5, offer.getDescription());
            cs.registerOutParameter(6, Types.BIGINT);
            cs.execute();

            long offerId = cs.getLong(6);
            if (offerId > 0) {
                offer.setId(offerId);
                notifyOwnerNewOffer(offer.getAdId(), offer.getUserId());
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Logica DAO: costruisce una descrizione di default per l'offerta
     * in base al suo tipo e all'eventuale prezzo proposto.
     * Non appartiene al database (che gestisce i vincoli strutturali)
     * né al layer di presentazione: è una regola di preparazione del dato.
     */
    private String buildDefaultDescription(Offer offer) {
        switch (offer.getType() != null ? offer.getType() : "") {
            case "SALE_BID":
                return offer.getPrice() != null
                        ? "Offerta di acquisto: EUR " + offer.getPrice().toPlainString()
                        : "Offerta di acquisto";
            case "EXCHANGE_BID":
                return "Proposta di scambio";
            case "GIFT_BID":
                return "Richiesta di donazione";
            default:
                return "Offerta";
        }
    }

    /**
     * Invia una notifica al proprietario dell'annuncio tramite sp_create_notification.
     */
    private void notifyOwnerNewOffer(Long adId, Long bidderId) {
        try {
            Long ownerId = null;
            String adTitle = null;
            PreparedStatement psAd = conn.prepareStatement("SELECT user_id, title FROM ads WHERE id = ?");
            psAd.setLong(1, adId);
            ResultSet rsAd = psAd.executeQuery();
            if (rsAd.next()) { ownerId = rsAd.getLong(1); adTitle = rsAd.getString(2); }
            if (ownerId == null) return;

            PreparedStatement psBidder = conn.prepareStatement("SELECT email FROM users WHERE id = ?");
            psBidder.setLong(1, bidderId);
            ResultSet rsBidder = psBidder.executeQuery();
            String bidderEmail = rsBidder.next() ? rsBidder.getString(1) : "Un utente";

            CallableStatement cs = conn.prepareCall("{CALL sp_create_notification(?,?,?,?)}");
            cs.setLong(1, ownerId);
            cs.setString(2, "Nuova offerta ricevuta");
            cs.setString(3, "Hai ricevuto una nuova offerta da " + bidderEmail + " per: " + adTitle);
            cs.setString(4, "/received_offers.jsp");
            cs.execute();
        } catch (Exception ignore) {
        }
    }

    /** Restituisce tutte le offerte relative a un annuncio, ordinate per data decrescente. */
    public List<Offer> getOffersByAdId(Long adId) {
        List<Offer> offers = new ArrayList<>();
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM offers WHERE ad_id = ? ORDER BY created_at DESC");
            ps.setLong(1, adId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) offers.add(createOfferFromResultSet(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return offers;
    }

    /** Restituisce tutte le offerte inviate da un utente, ordinate per data decrescente. */
    public List<Offer> getOffersByUserId(Long userId) {
        List<Offer> offers = new ArrayList<>();
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM offers WHERE user_id = ? ORDER BY created_at DESC");
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) offers.add(createOfferFromResultSet(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return offers;
    }

    /** Restituisce solo le offerte ACCEPTED inviate da un utente (usato da accepted_offers.jsp). */
    public List<Offer> getAcceptedOffersByUserId(Long userId) {
        List<Offer> offers = new ArrayList<>();
        try {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM offers WHERE user_id = ? AND status = 'ACCEPTED' ORDER BY created_at DESC");
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) offers.add(createOfferFromResultSet(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return offers;
    }

    /** Restituisce tutte le offerte ricevute sugli annunci di un dato utente (owner). */
    public List<Offer> getOffersReceivedByOwnerId(Long ownerUserId) {
        List<Offer> offers = new ArrayList<>();
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT o.* FROM offers o JOIN ads a ON o.ad_id = a.id WHERE a.user_id = ? ORDER BY o.created_at DESC");
            ps.setLong(1, ownerUserId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) offers.add(createOfferFromResultSet(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return offers;
    }

    /** Conta le offerte pendenti ricevute per gli annunci di un utente (usato per il badge). */
    public int countPendingOffersForOwner(Long ownerUserId) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM offers o JOIN ads a ON o.ad_id = a.id WHERE a.user_id = ? AND o.status = 'PENDING'");
            ps.setLong(1, ownerUserId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /** Conta le offerte PENDING per un singolo annuncio (usato da my_ads.jsp per ogni card). */
    public int countPendingOffers(Long adId) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM offers WHERE ad_id = ? AND status = 'PENDING'");
            ps.setLong(1, adId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /** Ritorna una singola offerta per ID. */
    public Offer getOfferById(Long id) {
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM offers WHERE id = ?");
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return createOfferFromResultSet(rs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Aggiorna lo stato di una singola offerta tramite sp_update_offer_status.
     * Usato per il ritiro (WITHDRAWN).
     */
    public boolean updateOfferStatus(Long offerId, String newStatus) {
        try {
            CallableStatement cs = conn.prepareCall("{CALL sp_update_offer_status(?,?)}");
            cs.setLong(1, offerId);
            cs.setString(2, newStatus);
            cs.execute();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Accetta un'offerta e crea la transazione corrispondente.
     * Delega all'intera sequenza atomica alla stored procedure sp_accept_offer,
     * che: accetta l'offerta, rigetta le altre pendenti, chiude l'annuncio
     * e crea la riga in transactions. Invia poi le notifiche tramite sp_create_notification.
     */
    public boolean acceptOfferAndCreateTransaction(Long offerId) {
        try {
            CallableStatement cs = conn.prepareCall("{CALL sp_accept_offer(?,?)}");
            cs.setLong(1, offerId);
            cs.registerOutParameter(2, Types.BIGINT);
            cs.execute();

            long transactionId = cs.getLong(2);
            if (transactionId > 0) {
                // Recupera i dati per le notifiche
                Offer offer = getOfferById(offerId);
                if (offer != null) {
                    PreparedStatement psAd = conn.prepareStatement("SELECT user_id, title FROM ads WHERE id = ?");
                    psAd.setLong(1, offer.getAdId());
                    ResultSet rsAd = psAd.executeQuery();
                    if (rsAd.next()) {
                        String adTitle = rsAd.getString("title");
                        Long sellerId = rsAd.getLong("user_id");

                        CallableStatement csN = conn.prepareCall("{CALL sp_create_notification(?,?,?,?)}");
                        csN.setLong(1, offer.getUserId());
                        csN.setString(2, "La tua offerta e stata accettata");
                        csN.setString(3, "La tua offerta per '" + adTitle + "' e stata accettata. Lascia una recensione!");
                        csN.setString(4, "/create_review.jsp?adId=" + offer.getAdId() + "&offerId=" + offerId);
                        csN.execute();
                    }
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Rifiuta un'offerta tramite sp_update_offer_status e invia una notifica
     * all'offerente tramite sp_create_notification.
     */
    public boolean rejectOffer(Long offerId) {
        try {
            CallableStatement cs = conn.prepareCall("{CALL sp_update_offer_status(?,?)}");
            cs.setLong(1, offerId);
            cs.setString(2, "REJECTED");
            cs.execute();

            Offer offer = getOfferById(offerId);
            if (offer != null) {
                PreparedStatement psAd = conn.prepareStatement("SELECT title FROM ads WHERE id = ?");
                psAd.setLong(1, offer.getAdId());
                ResultSet rs = psAd.executeQuery();
                String adTitle = rs.next() ? rs.getString(1) : "Annuncio";

                CallableStatement csN = conn.prepareCall("{CALL sp_create_notification(?,?,?,?)}");
                csN.setLong(1, offer.getUserId());
                csN.setString(2, "La tua offerta e stata rifiutata");
                csN.setString(3, "La tua offerta per '" + adTitle + "' e stata rifiutata.");
                csN.setString(4, "/sent_offers.jsp");
                csN.execute();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Acquisto diretto (Buy Now).
     * Delega l'intera logica atomica alla stored procedure sp_buy_now,
     * che: verifica che l'annuncio sia SALE e ACTIVE, crea l'offerta ACCEPTED,
     * rigetta le pendenti, chiude l'annuncio e crea la transazione.
     * Invia poi le notifiche a venditore e acquirente.
     */
    public boolean processBuyNow(Long adId, Long buyerId) {
        try {
            CallableStatement cs = conn.prepareCall("{CALL sp_buy_now(?,?,?)}");
            cs.setLong(1, adId);
            cs.setLong(2, buyerId);
            cs.registerOutParameter(3, Types.BIGINT);
            cs.execute();

            long transactionId = cs.getLong(3);
            if (transactionId > 0) {
                // Recupera dati per le notifiche
                PreparedStatement psAd = conn.prepareStatement("SELECT user_id, title FROM ads WHERE id = ?");
                psAd.setLong(1, adId);
                ResultSet rsAd = psAd.executeQuery();
                if (rsAd.next()) {
                    Long sellerId = rsAd.getLong("user_id");
                    String adTitle = rsAd.getString("title");

                    CallableStatement csNS = conn.prepareCall("{CALL sp_create_notification(?,?,?,?)}");
                    csNS.setLong(1, sellerId);
                    csNS.setString(2, "Articolo Acquistato!");
                    csNS.setString(3, "Il tuo annuncio '" + adTitle + "' e stato acquistato direttamente.");
                    csNS.setString(4, "/transactions.jsp");
                    csNS.execute();

                    // Recupera offerId per il link alla recensione
                    PreparedStatement psOffer = conn.prepareStatement(
                            "SELECT id FROM offers WHERE ad_id = ? AND user_id = ? AND status = 'ACCEPTED' ORDER BY created_at DESC LIMIT 1");
                    psOffer.setLong(1, adId);
                    psOffer.setLong(2, buyerId);
                    ResultSet rsOffer = psOffer.executeQuery();
                    long offerId = rsOffer.next() ? rsOffer.getLong(1) : 0;

                    CallableStatement csNB = conn.prepareCall("{CALL sp_create_notification(?,?,?,?)}");
                    csNB.setLong(1, buyerId);
                    csNB.setString(2, "Acquisto Completato!");
                    csNB.setString(3, "Hai acquistato '" + adTitle + "'. Lascia una recensione.");
                    csNB.setString(4, "/create_review.jsp?adId=" + adId + "&offerId=" + offerId);
                    csNB.execute();
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // ── Metodo privato mapper ResultSet → Offer ─────────────────────────────

    private Offer createOfferFromResultSet(ResultSet rs) throws SQLException {
        String type = rs.getString("type");
        Offer offer;
        if      ("SALE_BID".equalsIgnoreCase(type))     offer = new SaleBid();
        else if ("EXCHANGE_BID".equalsIgnoreCase(type)) offer = new ExchangeBid();
        else if ("GIFT_BID".equalsIgnoreCase(type))     offer = new GiftBid();
        else                                            offer = new Offer();

        offer.setId(rs.getLong("id"));
        offer.setAdId(rs.getLong("ad_id"));
        offer.setUserId(rs.getLong("user_id"));
        offer.setType(type);
        offer.setPrice(rs.getBigDecimal("price"));
        offer.setDescription(rs.getString("description"));
        offer.setStatus(rs.getString("status"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) offer.setCreatedAt(createdAt.toLocalDateTime());
        return offer;
    }
}
