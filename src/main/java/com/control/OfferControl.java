package com.control;

import java.sql.Connection;
import java.util.List;

import com.conn.DBConnect;
import com.dao.AdDAO;
import com.dao.NotificationDAO;
import com.dao.OfferDAO;
import com.entity.Ad;
import com.entity.Notification;
import com.entity.Offer;

public class OfferControl {

    public boolean hasPendingOffer(Long adId, Long userId) {
        Connection conn = DBConnect.getConn();
        return new OfferDAO(conn).hasPendingOffer(adId, userId);
    }

    public List<Offer> getAcceptedOffers(Long userId) {
        Connection conn = DBConnect.getConn();
        // Restituisce solo le offerte con status ACCEPTED (non tutte)
        return new OfferDAO(conn).getAcceptedOffersByUserId(userId);
    }

    public boolean createOffer(Offer offer) {
        Connection conn = DBConnect.getConn();
        return new OfferDAO(conn).createOffer(offer);
    }

    public Offer getOfferById(Long offerId) {
        Connection conn = DBConnect.getConn();
        return new OfferDAO(conn).getOfferById(offerId);
    }

    public List<Offer> getOffersByAdId(Long adId) {
        Connection conn = DBConnect.getConn();
        return new OfferDAO(conn).getOffersByAdId(adId);
    }

    public List<Offer> getOffersByUserId(Long userId) {
        Connection conn = DBConnect.getConn();
        return new OfferDAO(conn).getOffersByUserId(userId);
    }

    public List<Offer> getOffersReceivedByOwnerId(Long ownerUserId) {
        Connection conn = DBConnect.getConn();
        return new OfferDAO(conn).getOffersReceivedByOwnerId(ownerUserId);
    }

    public int countPendingOffersForOwner(Long ownerUserId) {
        Connection conn = DBConnect.getConn();
        return new OfferDAO(conn).countPendingOffersForOwner(ownerUserId);
    }

    /** Conta le offerte PENDING per un singolo annuncio. Chiamato da my_ads.jsp. */
    public int countPendingOffers(Long adId) {
        Connection conn = DBConnect.getConn();
        return new OfferDAO(conn).countPendingOffers(adId);
    }

    public boolean processBuyNow(Long adId, Long buyerId) {
        Connection conn = DBConnect.getConn();
        return new OfferDAO(conn).processBuyNow(adId, buyerId);
    }

    public boolean acceptOffer(Long offerId) {
        Connection conn = DBConnect.getConn();
        return new OfferDAO(conn).acceptOfferAndCreateTransaction(offerId);
    }

    public boolean rejectOffer(Long offerId) {
        Connection conn = DBConnect.getConn();
        return new OfferDAO(conn).rejectOffer(offerId);
    }

    /**
     * Ritira un'offerta e notifica il proprietario dell'annuncio.
     * Il ritiro dello stato avviene tramite sp_update_offer_status (DAO).
     * La notifica avviene tramite sp_create_notification (DAO).
     */
    public boolean withdrawOffer(Long offerId) {
        Connection conn = DBConnect.getConn();
        OfferDAO offerDAO = new OfferDAO(conn);

        Offer offer = offerDAO.getOfferById(offerId);
        if (offer == null) return false;

        boolean success = offerDAO.updateOfferStatus(offerId, "WITHDRAWN");

        if (success) {
            try {
                Ad ad = new AdDAO(conn).getAdById(offer.getAdId());
                if (ad != null) {
                    Notification notification = new Notification();
                    notification.setUserId(ad.getUserId());
                    notification.setTitle("Offerta ritirata");
                    notification.setMessage("Un'offerta per il tuo annuncio '" + ad.getTitle() + "' e stata ritirata.");
                    notification.setLink("/received_offers.jsp");
                    notification.setRead(false);
                    new NotificationDAO(conn).create(notification);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return success;
    }

    public Long getAdOwnerId(Long adId) {
        Connection conn = DBConnect.getConn();
        Ad ad = new AdDAO(conn).getAdById(adId);
        return ad != null ? ad.getUserId() : null;
    }
}
