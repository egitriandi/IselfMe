package com.egieTA.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;

@Controller
public class ScrapingController {
    private static final Logger logger = LoggerFactory.getLogger(ScrapingController.class);

    @Autowired
    private ScrapingService scrapingService;

    @GetMapping("/")
    public String home() {
        return "index";  // menampilkan halaman input
    }

    @GetMapping("/viewJournal")
    public String viewJournal(@RequestParam String url, Model model) {
        logger.info("Menerima request scraping untuk URL: {}", url);

        // Validasi URL
        if (url == null || url.isEmpty()) {
            logger.error("Error: URL tidak valid atau kosong.");
            model.addAttribute("errorMessage", "URL tidak valid atau kosong.");
            return "error"; // Redirect ke halaman error
        }

        // Lakukan scraping berdasarkan URL yang diterima
        Map<String, Map> result;

        try {
            result = scrapingService.scrapeJournal(url);
        }catch (Exception e) {
            logger.error("Gagal melakukan scraping: {}", e.getMessage());
            model.addAttribute("errorMessage", "Terjadi kesalahan saat mengambil data.");
            return "error"; // Redirect ke halaman error
        }

        List<Map<String, String>> scholarData = evoker((Map<String, Object>) result.get("scholarData"));
        List<Map<String, String>> garudaData = evoker((Map<String, Object>) result.get("garudaData"));

        model.addAttribute("forScholar", "Google Scholar");
        model.addAttribute("forScholarData", scholarData);
        model.addAttribute("forGaruda", "Garuda");
        model.addAttribute("forGarudaData", garudaData);

        return "viewJournal"; // Tampilan hasil scraping (HTML baru)
    }

    @PostMapping("/scrapeJournal")
    public ResponseEntity<String> scrapeJournal(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        if (url == null || url.isEmpty()) {
            return ResponseEntity.badRequest().body("URL tidak boleh kosong");
        }

        // Kirim URL tujuan sebagai respons
        String redirectUrl = "/viewJournal?url=" + url;
        return ResponseEntity.ok(redirectUrl);
    }

    private List evoker(Map portal){
        List returnList = new ArrayList();
        List judulList = new ArrayList();
        List yearList = new ArrayList();
        List linkList = new ArrayList();

        judulList.addAll((Collection) portal.get("titles"));
        yearList.addAll((Collection) portal.get("years"));
        linkList.addAll((Collection) portal.get("links"));

        for(int i = 0; i < judulList.size(); i++){
            String a = (String) judulList.get(i);
            String b = (String) yearList.get(i);
            String c = (String) linkList.get(i);
            String[] d = {a,b,c};

            returnList.add(d);
        }

        return returnList;
    }

    @PostMapping("/")
    public String scrape(@RequestParam(value = "portal", required = false) String portal,
                         @RequestParam("inputTitle") String inputTitle,
                         @RequestParam(value = "fromDate", required = false) String fromDate,
                         @RequestParam(value = "toDate", required = false) String toDate,
                         Model model) {
        // Validasi portal harus dipilih
        if (portal == null || portal.trim().isEmpty()) {
            model.addAttribute("errorMessage", "Silakan pilih minimal satu portal untuk melakukan pencarian.");
            return "index"; // Kembali ke halaman dengan pesan error
        }

        // Validasi tahun wajib diisi jika portal adalah Google Scholar atau GARUDA
        if ((portal.contains("0") || portal.contains("1")) && (fromDate == null || toDate == null || fromDate.isEmpty() || toDate.isEmpty())) {
            model.addAttribute("errorMessage", "Silakan isi jarak tahun untuk Google Scholar dan GARUDA.");
            return "index";
        }

        try {
            Map result = new HashMap();
            String setPortal = Constants.EMPTY_STRING;
            String[] tempPortal = portal.split("\\,");

            if(Arrays.asList(tempPortal).contains(Constants.STRING_ZERO)){
                setPortal = Constants.STRING_ZERO;
                List tempList = new ArrayList();
                result = scrapingService.getScholarURL(setPortal, inputTitle, fromDate, toDate, isSingleResult(inputTitle));
                tempList = scholarGarHandler(result, inputTitle);
                model.addAttribute("pickedPortalScholar", "Google Scholar");
                model.addAttribute("resultListScholar", tempList);
            }
            if(Arrays.asList(tempPortal).contains(Constants.STRING_ONE)){
                List tempList = new ArrayList();
                setPortal = Constants.STRING_ONE;
                result = scrapingService.getScholarURL(setPortal, inputTitle, fromDate, toDate, isSingleResult(inputTitle));
                tempList = scholarGarHandler(result, inputTitle);
                model.addAttribute("pickedPortalGaruda", "GARUDA");
                model.addAttribute("resultListGaruda", tempList);
            }
            if(Arrays.asList(tempPortal).contains(Constants.STRING_TWO)){
                List tempList = new ArrayList();
                result = scrapingService.getSintaURL(inputTitle);

                List titleList = new ArrayList();
                List collegeList = new ArrayList();
                List akreditasiList = new ArrayList();
                List linkList = new ArrayList();

                Set<String> uniqueSet = new HashSet<>(); // Set untuk memastikan keunikan data

                titleList.addAll((Collection) result.get("titleList"));
                collegeList.addAll((Collection) result.get("collegeList"));
                akreditasiList.addAll((Collection) result.get("akreditasiList"));
                linkList.addAll((Collection) result.get("linkList"));

                for(int i = 0; i < titleList.size(); i++){
                    String a = (String) titleList.get(i);
                    String b = (String) collegeList.get(i);
                    String c = (String) akreditasiList.get(i);
                    String d = (String) linkList.get(i);

                    if(a.equals(Constants.EMPTY_STRING) || b.equals(Constants.EMPTY_STRING) || c.equals(Constants.EMPTY_STRING) || d.equals(Constants.EMPTY_STRING)){
                        continue;
                    }

                    // Gabungkan semua atribut menjadi satu string unik
                    String uniqueKey = a + "|" + b + "|" + c + "|" + d;

                    if (!uniqueSet.contains(uniqueKey)) { // Periksa apakah sudah ada di set
                        uniqueSet.add(uniqueKey); // Tambahkan ke set jika belum ada
                    }
                }

                for(String tempText : uniqueSet){
                    String[] parts = tempText.split("\\|");
                    if (parts[2] != null) {
                        String[] temp = parts[2].split(Constants.SPACE, 3);
                        if (temp.length == 3) {
                            String akreditasiSpliter = temp[0] + Constants.SPACE + temp[1] + " || " + temp[2];
                            parts[2] = akreditasiSpliter;
                        }
                    }
                    if(tempList.size() == Constants.MAX_RECORD_SIZE){
                        break;
                    }else{
                        tempList.add(parts);
                    }
                }
                model.addAttribute("pickedPortalSinta", "SINTA");
                model.addAttribute("resultListSinta", tempList);
            }
            model.addAttribute("keyword", inputTitle);
        } catch (Exception e) {
            model.addAttribute("result", "Error: " + e.getMessage());
        }
//        return "result";
        return "index";
    }

    boolean isSingleResult(String query) {
        // 1. Jika ada tanda kutip, kemungkinan besar itu judul spesifik
        if (query.contains("\"")) {
            return true;
        }

        // 2. Hitung jumlah kata dalam input
        String[] words = query.trim().split("\\s+"); // Pisahkan berdasarkan spasi
        int wordCount = words.length;

        // Jika jumlah kata >= 6, kemungkinan besar itu judul lengkap
        if (wordCount >= 6) {
            return true;
        }

        // 3. Jika input mengandung angka (tahun), bisa jadi itu jurnal spesifik
        if (query.matches(".*\\b(19|20)\\d{2}\\b.*")) { // Cocokkan dengan tahun (1900-2099)
            return true;
        }

        // 4. Jika input terlihat seperti DOI atau ISBN
        if (query.matches("^10\\.\\d{4,9}/[-._;()/:A-Za-z0-9]+$") || query.matches("\\d{3}-\\d{1,5}-\\d{1,7}-\\d{1,7}-\\d{1}$")) {
            return true;
        }

        // Jika tidak memenuhi kriteria, anggap sebagai pencarian banyak hasil
        return false;
    }

    private List scholarGarHandler(Map result, String inputTitle){
        List returnList = new ArrayList();
        List judulList = new ArrayList();
        List yearList = new ArrayList();
        List linkList = new ArrayList();

        Set<String> uniqueSet = new HashSet<>(); // Set untuk memastikan keunikan data
        judulList.addAll((Collection) result.get("titleList"));
        yearList.addAll((Collection) result.get("yearList"));
        linkList.addAll((Collection) result.get("linkList"));

        int attempt = 0;
        if(isSingleResult(inputTitle)){
            attempt = 1;
        }else{
            attempt = Constants.MAX_RECORD_SIZE;
        }

        for(int i = 0; i < judulList.size(); i++){
            String a = (String) judulList.get(i);
            String b = (String) yearList.get(i);
            String c = (String) linkList.get(i);

            if(a.equals(Constants.EMPTY_STRING) || b.equals(Constants.EMPTY_STRING) || c.equals(Constants.EMPTY_STRING)){
                continue;
            }

            String uniqueKey = a + "|" + b + "|" + c;

            if (!uniqueSet.contains(uniqueKey)) { // Periksa apakah sudah ada di set
                uniqueSet.add(uniqueKey); // Tambahkan ke set jika belum ada
            }
        }

        for(String tempText : uniqueSet){
            String[] parts = tempText.split("\\|");
            if(returnList.size() == attempt){
                break;
            }else{
                returnList.add(parts);
            }
        }

        return returnList;
    }

}
