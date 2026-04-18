package com.entity;

public class ExchangeBid extends Offer {

    public ExchangeBid() {
        setType("EXCHANGE_BID");
    }

    public ExchangeBid(Long adId, Long userId, String description) {
        super(adId, userId, "EXCHANGE_BID", description);
    }
}
