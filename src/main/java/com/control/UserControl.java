package com.control;

import java.sql.Connection;

import com.conn.DBConnect;
import com.dao.UserDAO;
import com.entity.User;

public class UserControl {

    public User getUserById(Long id) {
        Connection conn = DBConnect.getConn();
        return new UserDAO(conn).getUserById(id);
    }

    public User getUserByEmail(String email) {
        Connection conn = DBConnect.getConn();
        return new UserDAO(conn).getUserByEmail(email);
    }

    public boolean updateUser(User user) {
        Connection conn = DBConnect.getConn();
        return new UserDAO(conn).updateUser(user);
    }

    public boolean changePassword(Long userId, String newPassword) {
        Connection conn = DBConnect.getConn();
        return new UserDAO(conn).changePassword(userId, newPassword);
    }

    /**
     * Restituisce un array [media_voti, numero_recensioni] per un utente.
     * Il calcolo è delegato alle funzioni SQL fn_get_user_avg_rating
     * e fn_count_user_reviews, che operano direttamente sul database.
     */
    public double[] getUserRating(Long userId) {
        double[] stats = { 0.0, 0.0 };
        try {
            Connection conn = DBConnect.getConn();
            java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT fn_get_user_avg_rating(?), fn_count_user_reviews(?)");
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            java.sql.ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                stats[0] = rs.getDouble(1);
                stats[1] = rs.getInt(2);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stats;
    }
}
