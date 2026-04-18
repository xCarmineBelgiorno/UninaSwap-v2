package com.entity;

public class GiftBid extends Offer {

    public GiftBid() {
        setType("GIFT_BID");
    }

    public GiftBid(Long adId, Long userId, String description) {
        super(adId, userId, "GIFT_BID", description);
    }
}
