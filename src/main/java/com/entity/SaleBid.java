package com.entity;

import java.math.BigDecimal;

public class SaleBid extends Offer {

    public SaleBid() {
        setType("SALE_BID");
    }

    public SaleBid(Long adId, Long userId, BigDecimal bidPrice, String message) {
        super(adId, userId, "SALE_BID", message);
        setPrice(bidPrice);
    }
}
