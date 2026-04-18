package com.dao;

import java.sql.*;

public class ReviewDAO {
    private final Connection conn;

    public ReviewDAO(Connection conn) {
        this.conn = conn;
    }

    /**
     * Value Object interno per trasportare le parti di una transazione
     * senza dover istanziare oggetti di dominio completi.
     */
    public static class TransactionParties {
        private final Long transactionId;
        private final Long buyerId;
        private final Long sellerId;
        private final Long adId;

        public TransactionParties(Long transactionId, Long buyerId, Long sellerId, Long adId) {
            this.transactionId = transactionId;
            this.buyerId = buyerId;
            this.sellerId = sellerId;
            this.adId = adId;
        }
        public Long getTransactionId() { return transactionId; }
        public Long getBuyerId()       { return buyerId; }
        public Long getSellerId()      { return sellerId; }
        public Long getAdId()          { return adId; }
    }

    /** Recupera le parti di una transazione tramite l'offerId collegata. */
    public TransactionParties getTransactionPartiesByOfferId(Long offerId) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, buyer_id, seller_id, ad_id FROM transactions WHERE offer_id = ?");
            ps.setLong(1, offerId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new TransactionParties(
                        rs.getLong("id"), rs.getLong("buyer_id"),
                        rs.getLong("seller_id"), rs.getLong("ad_id"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Verifica se esiste già una recensione per la coppia (transactionId, reviewerId).
     * Chiama la funzione SQL fn_has_review, delegando al database il controllo.
     */
    public boolean hasReview(Long transactionId, Long reviewerId) {
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT fn_has_review(?, ?)");
            ps.setLong(1, transactionId);
            ps.setLong(2, reviewerId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getBoolean(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Crea una recensione tramite la stored procedure sp_create_review.
     * Il DAO esegue una pre-validazione del rating nell'intervallo 1-5:
     * questo consente di restituire un errore applicativo chiaro e immediato
     * senza attendere il CHECK constraint del database, separando la
     * validazione del dato dalla verifica dell'integrità strutturale.
     * La procedura verifica poi internamente tramite fn_has_review che non
     * esista già una recensione, e il vincolo chk_reviews_not_self garantisce
     * che un utente non possa recensire sé stesso.
     */
    public boolean createReview(Long transactionId, Long reviewerId, Long reviewedUserId, int rating, String comment) {
        // Logica DAO: validazione anticipata del rating prima di qualsiasi
        // chiamata al database. Se il valore non è valido, il DAO fallisce subito.
        if (rating < 1 || rating > 5) {
            System.err.println("ReviewDAO: rating non valido (" + rating + "). Deve essere compreso tra 1 e 5.");
            return false;
        }
        try {
            CallableStatement cs = conn.prepareCall("{CALL sp_create_review(?,?,?,?,?)}");
            cs.setLong(1, transactionId);
            cs.setLong(2, reviewerId);
            cs.setLong(3, reviewedUserId);
            cs.setInt(4, rating);
            cs.setString(5, comment);
            cs.execute();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
