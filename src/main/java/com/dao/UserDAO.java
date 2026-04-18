package com.dao;

import com.entity.User;
import java.sql.*;

public class UserDAO {
    private final Connection conn;

    public UserDAO(Connection conn) {
        this.conn = conn;
    }

    /**
     * Registra un nuovo utente tramite la stored procedure sp_register_user.
     * Il database verifica il vincolo UNIQUE sull'email: se già esistente,
     * la procedura solleva un'eccezione che viene catturata restituendo false.
     */
    public boolean registerUser(User user) {
        try {
            CallableStatement cs = conn.prepareCall("{CALL sp_register_user(?,?,?,?,?,?,?,?)}");
            cs.setString(1, user.getEmail());
            cs.setString(2, user.getPassword());
            cs.setString(3, user.getFirstName());
            cs.setString(4, user.getLastName());
            cs.setString(5, user.getFaculty());
            cs.setString(6, user.getYear());
            cs.setString(7, user.getPhone());
            cs.registerOutParameter(8, Types.BIGINT);
            cs.execute();
            return cs.getLong(8) > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Autentica un utente tramite la funzione SQL fn_authenticate_user.
     * La funzione restituisce l'id se le credenziali sono valide, NULL altrimenti.
     * Se l'id è valido, carica il profilo completo con getUserById.
     */
    public User authenticateUser(String email, String password) {
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT fn_authenticate_user(?, ?)");
            ps.setString(1, email);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long userId = rs.getLong(1);
                if (userId > 0) return getUserById(userId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Recupera un utente per ID. */
    public User getUserById(Long id) {
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapUser(rs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Recupera un utente per email. */
    public User getUserByEmail(String email) {
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE email = ?");
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapUser(rs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Aggiorna il profilo anagrafico di un utente tramite sp_update_user.
     */
    public boolean updateUser(User user) {
        try {
            CallableStatement cs = conn.prepareCall("{CALL sp_update_user(?,?,?,?,?,?)}");
            cs.setLong(1, user.getId());
            cs.setString(2, user.getFirstName());
            cs.setString(3, user.getLastName());
            cs.setString(4, user.getFaculty());
            cs.setString(5, user.getYear());
            cs.setString(6, user.getPhone());
            cs.execute();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Cambia la password di un utente tramite sp_change_password.
     */
    public boolean changePassword(Long userId, String newPassword) {
        try {
            CallableStatement cs = conn.prepareCall("{CALL sp_change_password(?,?)}");
            cs.setLong(1, userId);
            cs.setString(2, newPassword);
            cs.execute();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ── Metodo privato mapper ResultSet → User ──────────────────────────────

    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setEmail(rs.getString("email"));
        u.setPassword(rs.getString("password"));
        u.setFirstName(rs.getString("first_name"));
        u.setLastName(rs.getString("last_name"));
        u.setFaculty(rs.getString("faculty"));
        u.setYear(rs.getString("year"));
        u.setPhone(rs.getString("phone"));
        u.setRole(rs.getString("role"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) u.setCreatedAt(createdAt.toLocalDateTime());
        return u;
    }
}
