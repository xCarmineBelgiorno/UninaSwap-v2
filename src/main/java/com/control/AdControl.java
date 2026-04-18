package com.control;

import java.sql.Connection;
import javax.servlet.http.Part;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.File;

import com.dao.AdDAO;
import com.entity.Ad;
import com.conn.DBConnect;

public class AdControl {

    // Helper for image upload
    public String processImageUpload(Part filePart, String srcPath, String runtimePath) {
        String fileName = null;
        try {
            if (filePart != null && filePart.getSize() > 0) {
                String partHeader = filePart.getHeader("content-disposition");
                String submittedFileName = null;
                for (String content : partHeader.split(";")) {
                    if (content.trim().startsWith("filename")) {
                        submittedFileName = content.substring(content.indexOf('=') + 1).trim().replace("\"", "");
                    }
                }

                if (submittedFileName != null && !submittedFileName.isEmpty()) {
                    File f = new File(submittedFileName);
                    submittedFileName = f.getName();

                    // Unique filename
                    fileName = System.currentTimeMillis() + "_" + submittedFileName.replaceAll("\\s+", "_");

                    // Ensure directories exist
                    File srcDir = new File(srcPath);
                    if (!srcDir.exists())
                        srcDir.mkdirs();

                    File runtimeDir = new File(runtimePath);
                    if (!runtimeDir.exists())
                        runtimeDir.mkdirs();

                    String srcFile = srcPath + File.separator + fileName;
                    String runtimeFile = runtimePath + File.separator + fileName;

                    // Read content
                    byte[] fileContent = filePart.getInputStream().readAllBytes();

                    // Write to both locations
                    Files.write(Paths.get(srcFile), fileContent);
                    Files.write(Paths.get(runtimeFile), fileContent);

                    System.out.println("Image saved to: " + srcFile);
                }
            }
        } catch (Exception e) {
            System.out.println("Error processing image: " + e.getMessage());
            e.printStackTrace();
        }
        return fileName;
    }

    public java.util.List<String> processMultipleImageUpload(java.util.Collection<Part> fileParts, String srcPath,
            String runtimePath) {
        java.util.List<String> fileNames = new java.util.ArrayList<>();
        try {
            for (Part filePart : fileParts) {
                if (filePart != null && filePart.getSize() > 0 && "images".equals(filePart.getName())) {
                    String uploadedName = processImageUpload(filePart, srcPath, runtimePath);
                    if (uploadedName != null) {
                        fileNames.add(uploadedName);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error processing multiple images: " + e.getMessage());
            e.printStackTrace();
        }
        return fileNames;
    }

    public boolean createAd(Ad ad) {
        Connection conn = DBConnect.getConn();
        AdDAO adDAO = new AdDAO(conn);
        return adDAO.createAd(ad);
    }

    public java.util.List<Ad> getAllAds() {
        Connection conn = DBConnect.getConn();
        AdDAO adDAO = new AdDAO(conn);
        return adDAO.getAllAds();
    }

    public java.util.List<Ad> getAdsByUserId(Long userId) {
        Connection conn = DBConnect.getConn();
        AdDAO adDAO = new AdDAO(conn);
        return adDAO.getAdsByUserId(userId);
    }

    public java.util.List<Ad> getAdsByCategory(String category) {
        Connection conn = DBConnect.getConn();
        AdDAO adDAO = new AdDAO(conn);
        return adDAO.getAdsByCategory(category);
    }

    public java.util.List<Ad> getAdsByType(String type) {
        Connection conn = DBConnect.getConn();
        AdDAO adDAO = new AdDAO(conn);
        return adDAO.getAdsByType(type);
    }

    public java.util.List<Ad> getAdsByFilters(String category, String type, java.math.BigDecimal minPrice,
            java.math.BigDecimal maxPrice) {
        Connection conn = DBConnect.getConn();
        AdDAO adDAO = new AdDAO(conn);
        return adDAO.getAdsByFilters(category, type, minPrice, maxPrice);
    }

    public Ad getAdById(Long id) {
        Connection conn = DBConnect.getConn();
        AdDAO adDAO = new AdDAO(conn);
        return adDAO.getAdById(id);
    }

    public boolean updateAd(Ad ad) {
        Connection conn = DBConnect.getConn();
        AdDAO adDAO = new AdDAO(conn);
        return adDAO.updateAd(ad);
    }

    public boolean deleteAd(Long id) {
        Connection conn = DBConnect.getConn();
        AdDAO adDAO = new AdDAO(conn);
        Ad ad = adDAO.getAdById(id);

        if (ad != null) {
            try {
                // Percorso calcolato dinamicamente dalla root del progetto Maven.
                // System.getProperty("user.dir") restituisce la directory da cui
                // mvn jetty:run viene lanciato, che corrisponde alla root del modulo.
                String srcPath = System.getProperty("user.dir")
                        + File.separator + "src"
                        + File.separator + "main"
                        + File.separator + "webapp"
                        + File.separator + "images";

                java.util.List<String> images = ad.getImageUrls();
                if (images != null) {
                    for (String img : images) {
                        if (img != null && !img.equals("default_ad.png")) {
                            File f = new File(srcPath + File.separator + img);
                            if (f.exists()) {
                                f.delete();
                                System.out.println("Deleted image: " + f.getAbsolutePath());
                            }
                        }
                    }
                }

                // Anche la vecchia image_url singola
                String oldImg = ad.getImageUrl();
                if (oldImg != null && !oldImg.equals("default_ad.png")) {
                    File f = new File(srcPath + File.separator + oldImg);
                    if (f.exists()) {
                        f.delete();
                        System.out.println("Deleted legacy image: " + f.getAbsolutePath());
                    }
                }

            } catch (Exception e) {
                System.out.println("Error deleting images: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return adDAO.deleteAd(id);
    }
}
