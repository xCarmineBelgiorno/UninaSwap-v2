package com.dao;

import com.entity.Notification;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotificationDAO {
    private final Connection conn;

    public NotificationDAO(Connection conn) { this.conn = conn; }

    /**
     * Crea una notifica per un utente tramite la stored procedure sp_create_notification.
     */
    public boolean create(Notification n) {
        try {
            CallableStatement cs = conn.prepareCall("{CALL sp_create_notification(?,?,?,?)}");
            cs.setLong(1, n.getUserId());
            cs.setString(2, n.getTitle());
            cs.setString(3, n.getMessage());
            cs.setString(4, n.getLink());
            cs.execute();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Recupera le notifiche di un utente, con possibilità di filtrare solo le non lette. */
    public List<Notification> getByUser(Long userId, boolean onlyUnread) {
        List<Notification> list = new ArrayList<>();
        try {
            String sql = "SELECT * FROM notifications WHERE user_id = ?"
                    + (onlyUnread ? " AND is_read = FALSE" : "")
                    + " ORDER BY created_at DESC";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(map(rs));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Marca una singola notifica come letta tramite sp_mark_notification_read.
     * La procedura include nella WHERE anche user_id come verifica di sicurezza.
     */
    public boolean markAsRead(Long id, Long userId) {
        try {
            CallableStatement cs = conn.prepareCall("{CALL sp_mark_notification_read(?,?)}");
            cs.setLong(1, id);
            cs.setLong(2, userId);
            cs.execute();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Marca tutte le notifiche non lette di un utente tramite sp_mark_all_notifications_read.
     */
    public int markAllAsRead(Long userId) {
        try {
            CallableStatement cs = conn.prepareCall("{CALL sp_mark_all_notifications_read(?)}");
            cs.setLong(1, userId);
            cs.execute();
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    private Notification map(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setId(rs.getLong("id"));
        n.setUserId(rs.getLong("user_id"));
        n.setTitle(rs.getString("title"));
        n.setMessage(rs.getString("message"));
        n.setLink(rs.getString("link"));
        n.setRead(rs.getBoolean("is_read"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) n.setCreatedAt(ts.toLocalDateTime());
        return n;
    }
}
