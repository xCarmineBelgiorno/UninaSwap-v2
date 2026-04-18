package com.servlet;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.conn.DBConnect;
import com.dao.ReviewDAO;
import com.dao.ReviewDAO.TransactionParties;
import com.entity.User;

public class CreateReviewServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private String buildRedirect(HttpServletRequest request, String redirect) {
        String ctx = request.getContextPath();
        String target = redirect;
        if (target == null || target.trim().isEmpty()) {
            target = "sent_offers.jsp";
        }
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target;
        }
        if (!target.startsWith("/")) {
            target = "/" + target;
        }
        if (ctx != null && !ctx.isEmpty() && !target.startsWith(ctx + "/")) {
            target = ctx + target;
        }
        return target;
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        String offerIdParam = request.getParameter("offerId");
        String ratingParam = request.getParameter("rating");
        String comment = request.getParameter("comment");
        String redirect = request.getParameter("redirect");

        String redirectTarget = buildRedirect(request, redirect);

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.sendRedirect("customerlogin.jsp");
            return;
        }
        User user = (User) session.getAttribute("user");

        if (offerIdParam == null || offerIdParam.trim().isEmpty()) {
            request.setAttribute("error", "Offerta non valida.");
            request.getRequestDispatcher("create_review.jsp").forward(request, response);
            return;
        }

        int rating;
        try {
            rating = Integer.parseInt(ratingParam);
        } catch (Exception e) {
            request.setAttribute("error", "Valutazione non valida.");
            request.setAttribute("offerId", offerIdParam);
            request.getRequestDispatcher("create_review.jsp").forward(request, response);
            return;
        }

        if (rating < 1 || rating > 5) {
            request.setAttribute("error", "La valutazione deve essere tra 1 e 5.");
            request.setAttribute("offerId", offerIdParam);
            request.getRequestDispatcher("create_review.jsp").forward(request, response);
            return;
        }

        Long offerId = Long.parseLong(offerIdParam);
        ReviewDAO reviewDAO = new ReviewDAO(DBConnect.getConn());
        TransactionParties parties = reviewDAO.getTransactionPartiesByOfferId(offerId);
        if (parties == null) {
            request.setAttribute("error", "Nessuna transazione associata a questa offerta.");
            request.setAttribute("offerId", offerIdParam);
            request.getRequestDispatcher("create_review.jsp").forward(request, response);
            return;
        }

        Long reviewerId = user.getId();
        Long buyerId = parties.getBuyerId();
        Long sellerId = parties.getSellerId();
        if (reviewerId == null || buyerId == null || sellerId == null) {
            request.setAttribute("error", "Dati incompleti per la recensione.");
            request.setAttribute("offerId", offerIdParam);
            request.getRequestDispatcher("create_review.jsp").forward(request, response);
            return;
        }

        if (!reviewerId.equals(buyerId)) {
            request.setAttribute("error", "Solo l'acquirente può lasciare una recensione.");
            request.setAttribute("offerId", offerIdParam);
            request.getRequestDispatcher("create_review.jsp").forward(request, response);
            return;
        }

        if (reviewDAO.hasReview(parties.getTransactionId(), reviewerId)) {
            response.sendRedirect(redirectTarget + (redirectTarget.contains("?") ? "&" : "?") + "error=1&action=review_exists");
            return;
        }

        Long reviewedUserId = sellerId;
        boolean ok = reviewDAO.createReview(parties.getTransactionId(), reviewerId, reviewedUserId, rating, comment);

        if (ok) {
            response.sendRedirect(redirectTarget + (redirectTarget.contains("?") ? "&" : "?") + "success=1&action=reviewed");
        } else {
            request.setAttribute("error", "Errore nel salvataggio della recensione.");
            request.setAttribute("offerId", offerIdParam);
            request.getRequestDispatcher("create_review.jsp").forward(request, response);
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher("create_review.jsp").forward(request, response);
    }
}

