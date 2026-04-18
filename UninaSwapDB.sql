-- ============================================================
-- UninaSwap - Schema fisico completo
-- Versione: 2.0
-- Tutta la logica di accesso ai dati e verifica dei vincoli
-- e' implementata come procedure/funzioni/trigger nel DB.
-- ============================================================

DROP DATABASE IF EXISTS UninaSwapDB;
CREATE DATABASE UninaSwapDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE UninaSwapDB;

-- ============================================================
-- TABELLE
-- ============================================================

-- Utenti della piattaforma (studenti e amministratori)
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    faculty VARCHAR(100),
    year VARCHAR(50),
    phone VARCHAR(20),
    role VARCHAR(20) DEFAULT 'STUDENT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_role CHECK (role IN ('STUDENT', 'ADMIN'))
);

-- Categorie degli annunci, popolate alla creazione del DB
CREATE TABLE categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT
);

INSERT INTO categories (name, description) VALUES
('Libri di testo',       'Libri universitari e materiale didattico'),
('Materiale informatico','Computer, tablet, accessori tecnologici'),
('Abbigliamento',        'Vestiti e accessori'),
('Sport e tempo libero', 'Attrezzature sportive e hobby'),
('Casa e arredamento',   'Mobili e oggetti per la casa'),
('Altro',                'Altri oggetti vari');

-- Annunci pubblicati dagli utenti.
-- Le tre tipologie (SALE, EXCHANGE, GIFT) sono unificate in questa tabella
-- tramite il campo 'type' come discriminante (Single Table Inheritance).
-- Il campo 'price' e' valorizzato solo per gli annunci di tipo SALE.
CREATE TABLE ads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(50) NOT NULL,
    category_id INT,
    price DECIMAL(10,2),
    location VARCHAR(255),
    pickup_time VARCHAR(100),
    image_url VARCHAR(255),           -- immagine principale (ridondanza controllata rispetto ad ad_images)
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)     REFERENCES users(id)      ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
    CONSTRAINT chk_ads_type   CHECK (type   IN ('SALE','EXCHANGE','GIFT')),
    CONSTRAINT chk_ads_status CHECK (status IN ('ACTIVE','SOLD','EXPIRED','DELETED')),
    CONSTRAINT chk_ads_price  CHECK (price IS NULL OR price >= 0)
);

-- Immagini aggiuntive degli annunci (attributo multivalore estratto in tabella separata)
CREATE TABLE ad_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ad_id BIGINT NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    FOREIGN KEY (ad_id) REFERENCES ads(id) ON DELETE CASCADE
);

-- Offerte inviate dagli utenti sugli annunci altrui.
-- Come per 'ads', le tre tipologie sono unificate con il campo 'type'.
-- Lo stato segue il ciclo: PENDING -> ACCEPTED | REJECTED | WITHDRAWN.
CREATE TABLE offers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ad_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    price DECIMAL(10,2),              -- valorizzato solo per SALE_BID
    description TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (ad_id)   REFERENCES ads(id)   ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_offers_type   CHECK (type   IN ('SALE_BID','EXCHANGE_BID','GIFT_BID')),
    CONSTRAINT chk_offers_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED','WITHDRAWN')),
    CONSTRAINT chk_offers_price  CHECK (price IS NULL OR price >= 0)
);

-- Transazioni concluse. Creata automaticamente da sp_accept_offer o sp_buy_now.
-- buyer_id e seller_id sono ridondanti rispetto a offers/ads ma semplificano le query.
CREATE TABLE transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ad_id BIGINT NOT NULL,
    offer_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    notes TEXT,
    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ad_id)     REFERENCES ads(id)   ON DELETE CASCADE,
    FOREIGN KEY (offer_id)  REFERENCES offers(id) ON DELETE CASCADE,
    FOREIGN KEY (buyer_id)  REFERENCES users(id)  ON DELETE CASCADE,
    FOREIGN KEY (seller_id) REFERENCES users(id)  ON DELETE CASCADE,
    CONSTRAINT chk_transactions_status CHECK (status IN ('COMPLETED','PENDING_DELIVERY','DELIVERED','CLOSED'))
);

-- Notifiche in-app generate dagli eventi del sistema (nuova offerta, accettazione, ecc.)
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    link VARCHAR(255),
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Recensioni lasciate al termine di una transazione.
-- Il vincolo uq_reviews_once impedisce doppie recensioni per la stessa transazione.
-- Il vincolo chk_reviews_not_self impedisce l'auto-recensione.
CREATE TABLE reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewed_user_id BIGINT NOT NULL,
    rating INTEGER NOT NULL,
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (transaction_id)    REFERENCES transactions(id) ON DELETE CASCADE,
    FOREIGN KEY (reviewer_id)       REFERENCES users(id)        ON DELETE CASCADE,
    FOREIGN KEY (reviewed_user_id)  REFERENCES users(id)        ON DELETE CASCADE,
    CONSTRAINT chk_reviews_rating   CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_not_self CHECK (reviewer_id <> reviewed_user_id),
    UNIQUE KEY uq_reviews_once (transaction_id, reviewer_id)
);

-- Utente admin di sistema
INSERT INTO users (email, password, first_name, last_name, role)
VALUES ('admin@uninaswap.it', 'admin', 'Sistema', 'Admin', 'ADMIN');


-- ============================================================
-- TRIGGER
-- ============================================================

-- Blocca inserimento offerte su annunci non attivi.
-- Il controllo avviene a livello di motore SQL, prima dell'INSERT,
-- cosi' e' immune da race condition tra thread Java paralleli.
DELIMITER $$
CREATE TRIGGER trg_offers_before_insert
BEFORE INSERT ON offers
FOR EACH ROW
BEGIN
    DECLARE ad_status VARCHAR(20);
    SELECT status INTO ad_status FROM ads WHERE id = NEW.ad_id;
    IF ad_status IS NULL OR ad_status <> 'ACTIVE' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Impossibile inviare offerte: annuncio non attivo.';
    END IF;
END$$
DELIMITER ;

-- Impedisce che un utente faccia un'offerta sul proprio annuncio.
DELIMITER $$
CREATE TRIGGER trg_offers_no_self_bid
BEFORE INSERT ON offers
FOR EACH ROW
BEGIN
    DECLARE owner_id BIGINT;
    SELECT user_id INTO owner_id FROM ads WHERE id = NEW.ad_id;
    IF owner_id = NEW.user_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Non puoi fare un offerta sul tuo stesso annuncio.';
    END IF;
END$$
DELIMITER ;

-- Mantieni updated_at aggiornato sugli annunci ad ogni modifica.
DELIMITER $$
CREATE TRIGGER trg_ads_update_timestamp
BEFORE UPDATE ON ads
FOR EACH ROW
BEGIN
    SET NEW.updated_at = CURRENT_TIMESTAMP;
END$$
DELIMITER ;

-- Mantieni updated_at aggiornato sulle offerte ad ogni modifica.
DELIMITER $$
CREATE TRIGGER trg_offers_update_timestamp
BEFORE UPDATE ON offers
FOR EACH ROW
BEGIN
    SET NEW.updated_at = CURRENT_TIMESTAMP;
END$$
DELIMITER ;


-- ============================================================
-- FUNZIONI
-- ============================================================

-- Risolve il nome di una categoria nel suo id.
-- Il fallback su 1 ("Libri di testo") evita inserimenti con category_id NULL.
DELIMITER $$
CREATE FUNCTION fn_get_category_id(p_name VARCHAR(100))
RETURNS INT
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_id INT DEFAULT 1;
    SELECT id INTO v_id FROM categories WHERE name = p_name LIMIT 1;
    RETURN v_id;
END$$
DELIMITER ;

-- Controlla se l'utente ha gia' un'offerta PENDING per quell'annuncio.
DELIMITER $$
CREATE FUNCTION fn_has_pending_offer(p_ad_id BIGINT, p_user_id BIGINT)
RETURNS BOOLEAN
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_count INT DEFAULT 0;
    SELECT COUNT(*) INTO v_count
    FROM offers
    WHERE ad_id = p_ad_id AND user_id = p_user_id AND status = 'PENDING';
    RETURN v_count > 0;
END$$
DELIMITER ;

-- Media dei voti ricevuti da un utente; restituisce 0.0 se non ha recensioni.
DELIMITER $$
CREATE FUNCTION fn_get_user_avg_rating(p_user_id BIGINT)
RETURNS DECIMAL(3,2)
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_avg DECIMAL(3,2) DEFAULT 0.00;
    SELECT COALESCE(AVG(rating), 0.00) INTO v_avg
    FROM reviews WHERE reviewed_user_id = p_user_id;
    RETURN v_avg;
END$$
DELIMITER ;

-- Numero totale di recensioni ricevute da un utente.
DELIMITER $$
CREATE FUNCTION fn_count_user_reviews(p_user_id BIGINT)
RETURNS INT
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_count INT DEFAULT 0;
    SELECT COUNT(*) INTO v_count
    FROM reviews WHERE reviewed_user_id = p_user_id;
    RETURN v_count;
END$$
DELIMITER ;

-- Verifica se esiste gia' una recensione per la coppia (transazione, autore).
DELIMITER $$
CREATE FUNCTION fn_has_review(p_transaction_id BIGINT, p_reviewer_id BIGINT)
RETURNS BOOLEAN
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_count INT DEFAULT 0;
    SELECT COUNT(*) INTO v_count
    FROM reviews
    WHERE transaction_id = p_transaction_id AND reviewer_id = p_reviewer_id;
    RETURN v_count > 0;
END$$
DELIMITER ;

-- Autentica un utente: restituisce l'id se email e password corrispondono, NULL altrimenti.
DELIMITER $$
CREATE FUNCTION fn_authenticate_user(p_email VARCHAR(255), p_password VARCHAR(255))
RETURNS BIGINT
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_id BIGINT DEFAULT NULL;
    SELECT id INTO v_id
    FROM users
    WHERE email = p_email AND password = p_password
    LIMIT 1;
    RETURN v_id;
END$$
DELIMITER ;


-- ============================================================
-- STORED PROCEDURE
-- ============================================================

-- Crea un annuncio. La categoria viene risolta per nome tramite fn_get_category_id.
-- Restituisce l'id generato via parametro OUT.
DELIMITER $$
CREATE PROCEDURE sp_create_ad(
    IN  p_user_id     BIGINT,
    IN  p_title       VARCHAR(255),
    IN  p_description TEXT,
    IN  p_type        VARCHAR(50),
    IN  p_category    VARCHAR(100),
    IN  p_price       DECIMAL(10,2),
    IN  p_location    VARCHAR(255),
    IN  p_pickup_time VARCHAR(100),
    IN  p_image_url   VARCHAR(255),
    OUT p_ad_id       BIGINT
)
BEGIN
    DECLARE v_category_id INT;
    SET v_category_id = fn_get_category_id(p_category);

    INSERT INTO ads (user_id, title, description, type, category_id, price,
                     location, pickup_time, image_url, status)
    VALUES (p_user_id, p_title, p_description, p_type, v_category_id, p_price,
            p_location, p_pickup_time, p_image_url, 'ACTIVE');

    SET p_ad_id = LAST_INSERT_ID();
END$$
DELIMITER ;

-- Aggiunge un'immagine aggiuntiva a un annuncio esistente.
DELIMITER $$
CREATE PROCEDURE sp_add_ad_image(
    IN p_ad_id    BIGINT,
    IN p_image_url VARCHAR(255)
)
BEGIN
    INSERT INTO ad_images (ad_id, image_url) VALUES (p_ad_id, p_image_url);
END$$
DELIMITER ;

-- Cancella tutte le immagini di un annuncio. Usata prima di reinserirle in blocco (update).
DELIMITER $$
CREATE PROCEDURE sp_replace_ad_images(IN p_ad_id BIGINT)
BEGIN
    DELETE FROM ad_images WHERE ad_id = p_ad_id;
END$$
DELIMITER ;

-- Aggiorna i campi di un annuncio.
DELIMITER $$
CREATE PROCEDURE sp_update_ad(
    IN p_ad_id      BIGINT,
    IN p_title      VARCHAR(255),
    IN p_description TEXT,
    IN p_type       VARCHAR(50),
    IN p_category   VARCHAR(100),
    IN p_price      DECIMAL(10,2),
    IN p_location   VARCHAR(255),
    IN p_pickup_time VARCHAR(100),
    IN p_image_url  VARCHAR(255),
    IN p_status     VARCHAR(20)
)
BEGIN
    DECLARE v_category_id INT;
    SET v_category_id = fn_get_category_id(p_category);

    UPDATE ads
    SET title        = p_title,
        description  = p_description,
        type         = p_type,
        category_id  = v_category_id,
        price        = p_price,
        location     = p_location,
        pickup_time  = p_pickup_time,
        image_url    = p_image_url,
        status       = p_status
    WHERE id = p_ad_id;
END$$
DELIMITER ;

-- Elimina un annuncio. La cascata sulle FK gestisce automaticamente
-- ad_images e le offerte collegate.
DELIMITER $$
CREATE PROCEDURE sp_delete_ad(IN p_ad_id BIGINT)
BEGIN
    DELETE FROM ads WHERE id = p_ad_id;
END$$
DELIMITER ;

-- Registra un nuovo utente con ruolo STUDENT. Restituisce l'id generato.
DELIMITER $$
CREATE PROCEDURE sp_register_user(
    IN  p_email      VARCHAR(255),
    IN  p_password   VARCHAR(255),
    IN  p_first_name VARCHAR(100),
    IN  p_last_name  VARCHAR(100),
    IN  p_faculty    VARCHAR(100),
    IN  p_year       VARCHAR(50),
    IN  p_phone      VARCHAR(20),
    OUT p_user_id    BIGINT
)
BEGIN
    INSERT INTO users (email, password, first_name, last_name, faculty, year, phone, role)
    VALUES (p_email, p_password, p_first_name, p_last_name, p_faculty, p_year, p_phone, 'STUDENT');
    SET p_user_id = LAST_INSERT_ID();
END$$
DELIMITER ;

-- Aggiorna i dati anagrafici di un utente (non tocca email e password).
DELIMITER $$
CREATE PROCEDURE sp_update_user(
    IN p_user_id   BIGINT,
    IN p_first_name VARCHAR(100),
    IN p_last_name  VARCHAR(100),
    IN p_faculty    VARCHAR(100),
    IN p_year       VARCHAR(50),
    IN p_phone      VARCHAR(20)
)
BEGIN
    UPDATE users
    SET first_name = p_first_name,
        last_name  = p_last_name,
        faculty    = p_faculty,
        year       = p_year,
        phone      = p_phone
    WHERE id = p_user_id;
END$$
DELIMITER ;

-- Cambia la password di un utente.
DELIMITER $$
CREATE PROCEDURE sp_change_password(
    IN p_user_id     BIGINT,
    IN p_new_password VARCHAR(255)
)
BEGIN
    UPDATE users SET password = p_new_password WHERE id = p_user_id;
END$$
DELIMITER ;

-- Inserisce una nuova offerta.
-- I trigger trg_offers_before_insert (annuncio ACTIVE) e trg_offers_no_self_bid
-- vengono eseguiti automaticamente prima dell'INSERT.
DELIMITER $$
CREATE PROCEDURE sp_create_offer(
    IN  p_ad_id      BIGINT,
    IN  p_user_id    BIGINT,
    IN  p_type       VARCHAR(50),
    IN  p_price      DECIMAL(10,2),
    IN  p_description TEXT,
    OUT p_offer_id   BIGINT
)
BEGIN
    INSERT INTO offers (ad_id, user_id, type, price, description, status)
    VALUES (p_ad_id, p_user_id, p_type, p_price, p_description, 'PENDING');
    SET p_offer_id = LAST_INSERT_ID();
END$$
DELIMITER ;

-- Aggiorna lo stato di un'offerta (es. WITHDRAWN quando l'utente la ritira).
DELIMITER $$
CREATE PROCEDURE sp_update_offer_status(
    IN p_offer_id BIGINT,
    IN p_status   VARCHAR(20)
)
BEGIN
    UPDATE offers SET status = p_status WHERE id = p_offer_id;
END$$
DELIMITER ;

-- Crea una notifica per un utente.
DELIMITER $$
CREATE PROCEDURE sp_create_notification(
    IN p_user_id BIGINT,
    IN p_title   VARCHAR(150),
    IN p_message TEXT,
    IN p_link    VARCHAR(255)
)
BEGIN
    INSERT INTO notifications (user_id, title, message, link, is_read)
    VALUES (p_user_id, p_title, p_message, p_link, FALSE);
END$$
DELIMITER ;

-- Marca una notifica come letta. La condizione su user_id impedisce
-- che un utente possa leggere notifiche di altri.
DELIMITER $$
CREATE PROCEDURE sp_mark_notification_read(
    IN p_notification_id BIGINT,
    IN p_user_id         BIGINT
)
BEGIN
    UPDATE notifications
    SET is_read = TRUE
    WHERE id = p_notification_id AND user_id = p_user_id;
END$$
DELIMITER ;

-- Marca tutte le notifiche non lette di un utente come lette.
DELIMITER $$
CREATE PROCEDURE sp_mark_all_notifications_read(IN p_user_id BIGINT)
BEGIN
    UPDATE notifications
    SET is_read = TRUE
    WHERE user_id = p_user_id AND is_read = FALSE;
END$$
DELIMITER ;

-- Accetta un'offerta e gestisce tutto il resto atomicamente:
-- rifiuta le altre offerte pendenti sullo stesso annuncio,
-- imposta l'annuncio a SOLD e crea la transazione.
-- Restituisce l'id della transazione via parametro OUT.
DELIMITER $$
CREATE PROCEDURE sp_accept_offer(
    IN  p_offer_id      BIGINT,
    OUT p_transaction_id BIGINT
)
BEGIN
    DECLARE v_ad_id     BIGINT;
    DECLARE v_buyer_id  BIGINT;
    DECLARE v_seller_id BIGINT;

    -- Leggi dati dell'offerta e del relativo annuncio in un'unica query
    SELECT o.ad_id, o.user_id, a.user_id
    INTO   v_ad_id,  v_buyer_id, v_seller_id
    FROM   offers o
    JOIN   ads a ON a.id = o.ad_id
    WHERE  o.id = p_offer_id;

    UPDATE offers SET status = 'ACCEPTED' WHERE id = p_offer_id;

    -- Rifiuta le offerte pendenti degli altri utenti sullo stesso annuncio
    UPDATE offers
    SET status = 'REJECTED'
    WHERE ad_id = v_ad_id AND id <> p_offer_id AND status = 'PENDING';

    UPDATE ads SET status = 'SOLD' WHERE id = v_ad_id;

    INSERT INTO transactions (ad_id, offer_id, buyer_id, seller_id, status, notes)
    VALUES (v_ad_id, p_offer_id, v_buyer_id, v_seller_id, 'COMPLETED',
            'Offerta accettata dal proprietario');

    SET p_transaction_id = LAST_INSERT_ID();
END$$
DELIMITER ;

-- Acquisto diretto (Compralo Subito). Disponibile solo per annunci SALE ACTIVE.
-- Crea l'offerta gia' in stato ACCEPTED, rifiuta le eventuali pendenti,
-- chiude l'annuncio e crea la transazione. Tutto atomico.
DELIMITER $$
CREATE PROCEDURE sp_buy_now(
    IN  p_ad_id          BIGINT,
    IN  p_buyer_id       BIGINT,
    OUT p_transaction_id BIGINT
)
BEGIN
    DECLARE v_seller_id BIGINT;
    DECLARE v_price     DECIMAL(10,2);
    DECLARE v_ad_status VARCHAR(20);
    DECLARE v_ad_type   VARCHAR(50);
    DECLARE v_offer_id  BIGINT;

    SELECT user_id, price, status, type
    INTO   v_seller_id, v_price, v_ad_status, v_ad_type
    FROM   ads WHERE id = p_ad_id;

    -- Verifica precondizioni: annuncio deve essere SALE e ACTIVE
    IF v_ad_status IS NULL OR v_ad_status <> 'ACTIVE' OR v_ad_type <> 'SALE' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Acquisto diretto non possibile: annuncio non attivo o non in vendita.';
    END IF;

    -- Il trigger trg_offers_before_insert non blocca perche' l'annuncio e' ACTIVE
    INSERT INTO offers (ad_id, user_id, type, price, description, status)
    VALUES (p_ad_id, p_buyer_id, 'SALE_BID', v_price, 'Acquisto diretto (Compralo Subito)', 'ACCEPTED');

    SET v_offer_id = LAST_INSERT_ID();

    UPDATE offers
    SET status = 'REJECTED'
    WHERE ad_id = p_ad_id AND id <> v_offer_id AND status = 'PENDING';

    UPDATE ads SET status = 'SOLD' WHERE id = p_ad_id;

    INSERT INTO transactions (ad_id, offer_id, buyer_id, seller_id, status, notes)
    VALUES (p_ad_id, v_offer_id, p_buyer_id, v_seller_id, 'COMPLETED',
            'Acquisto diretto (Compralo Subito)');

    SET p_transaction_id = LAST_INSERT_ID();
END$$
DELIMITER ;

-- Crea una recensione per una transazione conclusa.
-- Controlla prima tramite fn_has_review che non ne esista gia' una.
DELIMITER $$
CREATE PROCEDURE sp_create_review(
    IN p_transaction_id  BIGINT,
    IN p_reviewer_id     BIGINT,
    IN p_reviewed_user_id BIGINT,
    IN p_rating          INT,
    IN p_comment         TEXT
)
BEGIN
    IF fn_has_review(p_transaction_id, p_reviewer_id) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Hai gia lasciato una recensione per questa transazione.';
    END IF;

    INSERT INTO reviews (transaction_id, reviewer_id, reviewed_user_id, rating, comment)
    VALUES (p_transaction_id, p_reviewer_id, p_reviewed_user_id, p_rating, p_comment);
END$$
DELIMITER ;
