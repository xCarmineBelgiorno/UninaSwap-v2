<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.entity.User" %>

<%
    User user = (User) session.getAttribute("user");
    if (user == null) {
        response.sendRedirect("customerlogin.jsp");
        return;
    }
    String offerId = request.getParameter("offerId");
    if (offerId == null) {
        Object attr = request.getAttribute("offerId");
        if (attr != null) offerId = String.valueOf(attr);
    }
    String redirect = request.getParameter("redirect");
    if (redirect == null || redirect.trim().isEmpty()) {
        redirect = "sent_offers.jsp";
    }
    String error = (String) request.getAttribute("error");
%>

<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Lascia una recensione - UninaSwap</title>
    <link rel="stylesheet" href="Css/w3.css">
    <link rel="stylesheet" href="Css/abc.css">
    <style>
        .container {
            max-width: 700px;
            margin: 40px auto;
            background: #ffffff;
            border-radius: 14px;
            box-shadow: 0 6px 24px rgba(0,0,0,0.08);
            padding: 30px;
        }
        .title {
            font-size: 24px;
            font-weight: 700;
            color: #1e293b;
            margin-bottom: 20px;
        }
        .field {
            margin-bottom: 16px;
        }
        .field label {
            display: block;
            margin-bottom: 6px;
            font-weight: 600;
            color: #334155;
        }
        .field input, .field select, .field textarea {
            width: 100%;
            border: 1px solid #e2e8f0;
            border-radius: 10px;
            padding: 10px 12px;
            font-size: 14px;
        }
        .actions {
            display: flex;
            gap: 12px;
            margin-top: 10px;
        }
        .btn {
            padding: 10px 16px;
            border-radius: 10px;
            border: none;
            cursor: pointer;
            font-weight: 600;
        }
        .btn-primary {
            background: #667eea;
            color: #fff;
        }
        .btn-secondary {
            background: #e2e8f0;
            color: #334155;
            text-decoration: none;
            display: inline-flex;
            align-items: center;
        }
        .alert {
            background: #fee2e2;
            color: #991b1b;
            padding: 10px 12px;
            border-radius: 10px;
            margin-bottom: 16px;
        }
    </style>
</head>
<body style="background:#f1f5f9; min-height:100vh;">

<jsp:include page="navbar.jsp" />

<div class="container">
    <div class="title">Lascia una recensione</div>

    <% if (error != null) { %>
        <div class="alert"><%= error %></div>
    <% } %>

    <% if (offerId == null || offerId.trim().isEmpty()) { %>
        <div class="alert">Offerta non valida.</div>
        <a class="btn btn-secondary" href="sent_offers.jsp">Torna alle offerte</a>
    <% } else { %>
        <form action="review/create" method="post">
            <input type="hidden" name="offerId" value="<%= offerId %>">
            <input type="hidden" name="redirect" value="<%= redirect %>">

            <div class="field">
                <label for="rating">Valutazione (1-5)</label>
                <select id="rating" name="rating" required>
                    <option value="">Seleziona</option>
                    <option value="5">5 - Eccellente</option>
                    <option value="4">4 - Buono</option>
                    <option value="3">3 - Discreto</option>
                    <option value="2">2 - Scarso</option>
                    <option value="1">1 - Pessimo</option>
                </select>
            </div>

            <div class="field">
                <label for="comment">Commento (facoltativo)</label>
                <textarea id="comment" name="comment" rows="4" placeholder="Scrivi un commento..."></textarea>
            </div>

            <div class="actions">
                <button type="submit" class="btn btn-primary">Invia recensione</button>
                <a class="btn btn-secondary" href="<%= redirect %>">Annulla</a>
            </div>
        </form>
    <% } %>
</div>

</body>
</html>
