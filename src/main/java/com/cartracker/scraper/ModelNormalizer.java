package com.cartracker.scraper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a noisy olx.ba title into a canonical, comparable model name so cohort
 * pricing works. A curated, specificity-ordered keyword map is matched against
 * the title with word boundaries; the first hit wins. Falls back to the first
 * two words after the brand. Heuristic and explainable - extend the map as new
 * models appear. This is the single source of truth; db/backfill_model.sql is
 * generated from the same map.
 */
public final class ModelNormalizer {

  private static final Map<String, String> MODEL_KEYWORDS = new LinkedHashMap<>();

  static {
    MODEL_KEYWORDS.put("Golf 7", "Golf 7");
    MODEL_KEYWORDS.put("Golf 8", "Golf 8");
    MODEL_KEYWORDS.put("Golf", "Golf");
    MODEL_KEYWORDS.put("Passat", "Passat");
    MODEL_KEYWORDS.put("Polo", "Polo");
    MODEL_KEYWORDS.put("Tiguan", "Tiguan");
    MODEL_KEYWORDS.put("Touareg", "Touareg");
    MODEL_KEYWORDS.put("Touran", "Touran");
    MODEL_KEYWORDS.put("Arteon", "Arteon");
    MODEL_KEYWORDS.put("T-Cross", "T-Cross");
    MODEL_KEYWORDS.put("T-Roc", "T-Roc");
    MODEL_KEYWORDS.put("Up", "Up");
    MODEL_KEYWORDS.put("Caddy", "Caddy");
    MODEL_KEYWORDS.put("Transporter", "Transporter");
    MODEL_KEYWORDS.put("Amarok", "Amarok");
    MODEL_KEYWORDS.put("Scirocco", "Scirocco");
    MODEL_KEYWORDS.put("Beetle", "Beetle");
    MODEL_KEYWORDS.put("Jetta", "Jetta");
    MODEL_KEYWORDS.put("e-tron", "e-tron");
    MODEL_KEYWORDS.put("Q8", "Q8");
    MODEL_KEYWORDS.put("Q7", "Q7");
    MODEL_KEYWORDS.put("Q5", "Q5");
    MODEL_KEYWORDS.put("Q3", "Q3");
    MODEL_KEYWORDS.put("Q2", "Q2");
    MODEL_KEYWORDS.put("SQ5", "SQ5");
    MODEL_KEYWORDS.put("A8", "A8");
    MODEL_KEYWORDS.put("A7", "A7");
    MODEL_KEYWORDS.put("A6", "A6");
    MODEL_KEYWORDS.put("A5", "A5");
    MODEL_KEYWORDS.put("A4", "A4");
    MODEL_KEYWORDS.put("A3", "A3");
    MODEL_KEYWORDS.put("A2", "A2");
    MODEL_KEYWORDS.put("A1", "A1");
    MODEL_KEYWORDS.put("TT", "TT");
    MODEL_KEYWORDS.put("R8", "R8");
    MODEL_KEYWORDS.put("RS", "RS");
    MODEL_KEYWORDS.put("X7", "X7");
    MODEL_KEYWORDS.put("X6", "X6");
    MODEL_KEYWORDS.put("X5", "X5");
    MODEL_KEYWORDS.put("X4", "X4");
    MODEL_KEYWORDS.put("X3", "X3");
    MODEL_KEYWORDS.put("X2", "X2");
    MODEL_KEYWORDS.put("X1", "X1");
    MODEL_KEYWORDS.put("M5", "M5");
    MODEL_KEYWORDS.put("M4", "M4");
    MODEL_KEYWORDS.put("M3", "M3");
    MODEL_KEYWORDS.put("M2", "M2");
    MODEL_KEYWORDS.put("Z4", "Z4");
    MODEL_KEYWORDS.put("i8", "i8");
    MODEL_KEYWORDS.put("i3", "i3");
    MODEL_KEYWORDS.put("GLA", "GLA");
    MODEL_KEYWORDS.put("GLB", "GLB");
    MODEL_KEYWORDS.put("GLC", "GLC");
    MODEL_KEYWORDS.put("GLE", "GLE");
    MODEL_KEYWORDS.put("GLK", "GLK");
    MODEL_KEYWORDS.put("CLA", "CLA");
    MODEL_KEYWORDS.put("CLS", "CLS");
    MODEL_KEYWORDS.put("Vito", "Vito");
    MODEL_KEYWORDS.put("Sprinter", "Sprinter");
    MODEL_KEYWORDS.put("Octavia", "Octavia");
    MODEL_KEYWORDS.put("Superb", "Superb");
    MODEL_KEYWORDS.put("Fabia", "Fabia");
    MODEL_KEYWORDS.put("Rapid", "Rapid");
    MODEL_KEYWORDS.put("Kodiaq", "Kodiaq");
    MODEL_KEYWORDS.put("Karoq", "Karoq");
    MODEL_KEYWORDS.put("Scala", "Scala");
    MODEL_KEYWORDS.put("Yeti", "Yeti");
    MODEL_KEYWORDS.put("Enyaq", "Enyaq");
    MODEL_KEYWORDS.put("Roomster", "Roomster");
    MODEL_KEYWORDS.put("Altea", "Altea");
    MODEL_KEYWORDS.put("Tucson", "Tucson");
    MODEL_KEYWORDS.put("Santa Fe", "Santa Fe");
    MODEL_KEYWORDS.put("Kona", "Kona");
    MODEL_KEYWORDS.put("Elantra", "Elantra");
    MODEL_KEYWORDS.put("i40", "i40");
    MODEL_KEYWORDS.put("i30", "i30");
    MODEL_KEYWORDS.put("i20", "i20");
    MODEL_KEYWORDS.put("i10", "i10");
    MODEL_KEYWORDS.put("Getz", "Getz");
    MODEL_KEYWORDS.put("ix20", "ix20");
    MODEL_KEYWORDS.put("Sportage", "Sportage");
    MODEL_KEYWORDS.put("Sorento", "Sorento");
    MODEL_KEYWORDS.put("Ceed", "Ceed");
    MODEL_KEYWORDS.put("Rio", "Rio");
    MODEL_KEYWORDS.put("Picanto", "Picanto");
    MODEL_KEYWORDS.put("Stonic", "Stonic");
    MODEL_KEYWORDS.put("Niro", "Niro");
    MODEL_KEYWORDS.put("Seltos", "Seltos");
    MODEL_KEYWORDS.put("Corsa", "Corsa");
    MODEL_KEYWORDS.put("Astra", "Astra");
    MODEL_KEYWORDS.put("Insignia", "Insignia");
    MODEL_KEYWORDS.put("Mokka", "Mokka");
    MODEL_KEYWORDS.put("Crossland", "Crossland");
    MODEL_KEYWORDS.put("Grandland", "Grandland");
    MODEL_KEYWORDS.put("Zafira", "Zafira");
    MODEL_KEYWORDS.put("Vivaro", "Vivaro");
    MODEL_KEYWORDS.put("Captur", "Captur");
    MODEL_KEYWORDS.put("Clio", "Clio");
    MODEL_KEYWORDS.put("Megane", "Megane");
    MODEL_KEYWORDS.put("Scenic", "Scenic");
    MODEL_KEYWORDS.put("Kadjar", "Kadjar");
    MODEL_KEYWORDS.put("Koleos", "Koleos");
    MODEL_KEYWORDS.put("Talisman", "Talisman");
    MODEL_KEYWORDS.put("Symbol", "Symbol");
    MODEL_KEYWORDS.put("Focus", "Focus");
    MODEL_KEYWORDS.put("Fiesta", "Fiesta");
    MODEL_KEYWORDS.put("Mondeo", "Mondeo");
    MODEL_KEYWORDS.put("Kuga", "Kuga");
    MODEL_KEYWORDS.put("EcoSport", "EcoSport");
    MODEL_KEYWORDS.put("Puma", "Puma");
    MODEL_KEYWORDS.put("Transit", "Transit");
    MODEL_KEYWORDS.put("Ranger", "Ranger");
    MODEL_KEYWORDS.put("3008", "3008");
    MODEL_KEYWORDS.put("5008", "5008");
    MODEL_KEYWORDS.put("2008", "2008");
    MODEL_KEYWORDS.put("308", "308");
    MODEL_KEYWORDS.put("208", "208");
    MODEL_KEYWORDS.put("508", "508");
    MODEL_KEYWORDS.put("Partner", "Partner");
    MODEL_KEYWORDS.put("407", "407");
    MODEL_KEYWORDS.put("C3", "C3");
    MODEL_KEYWORDS.put("C4", "C4");
    MODEL_KEYWORDS.put("C5", "C5");
    MODEL_KEYWORDS.put("C1", "C1");
    MODEL_KEYWORDS.put("Berlingo", "Berlingo");
    MODEL_KEYWORDS.put("500", "500");
    MODEL_KEYWORDS.put("Punto", "Punto");
    MODEL_KEYWORDS.put("Tipo", "Tipo");
    MODEL_KEYWORDS.put("Doblo", "Doblo");
    MODEL_KEYWORDS.put("Panda", "Panda");
    MODEL_KEYWORDS.put("Ducato", "Ducato");
    MODEL_KEYWORDS.put("Ibiza", "Ibiza");
    MODEL_KEYWORDS.put("Leon", "Leon");
    MODEL_KEYWORDS.put("Arona", "Arona");
    MODEL_KEYWORDS.put("Ateca", "Ateca");
    MODEL_KEYWORDS.put("Tarraco", "Tarraco");
    MODEL_KEYWORDS.put("Alhambra", "Alhambra");
    MODEL_KEYWORDS.put("Toledo", "Toledo");
    MODEL_KEYWORDS.put("Cupra", "Cupra");
    MODEL_KEYWORDS.put("Formentor", "Formentor");
    MODEL_KEYWORDS.put("Corolla", "Corolla");
    MODEL_KEYWORDS.put("Yaris", "Yaris");
    MODEL_KEYWORDS.put("RAV4", "RAV4");
    MODEL_KEYWORDS.put("C-HR", "C-HR");
    MODEL_KEYWORDS.put("Auris", "Auris");
    MODEL_KEYWORDS.put("Avensis", "Avensis");
    MODEL_KEYWORDS.put("Hilux", "Hilux");
    MODEL_KEYWORDS.put("Prius", "Prius");
    MODEL_KEYWORDS.put("Qashqai", "Qashqai");
    MODEL_KEYWORDS.put("X-Trail", "X-Trail");
    MODEL_KEYWORDS.put("Juke", "Juke");
    MODEL_KEYWORDS.put("Micra", "Micra");
    MODEL_KEYWORDS.put("Leaf", "Leaf");
    MODEL_KEYWORDS.put("Navara", "Navara");
    MODEL_KEYWORDS.put("XC40", "XC40");
    MODEL_KEYWORDS.put("XC60", "XC60");
    MODEL_KEYWORDS.put("XC90", "XC90");
    MODEL_KEYWORDS.put("V40", "V40");
    MODEL_KEYWORDS.put("V60", "V60");
    MODEL_KEYWORDS.put("V90", "V90");
    MODEL_KEYWORDS.put("S60", "S60");
    MODEL_KEYWORDS.put("S90", "S90");
    MODEL_KEYWORDS.put("Cayenne", "Cayenne");
    MODEL_KEYWORDS.put("Macan", "Macan");
    MODEL_KEYWORDS.put("Panamera", "Panamera");
    MODEL_KEYWORDS.put("911", "911");
    MODEL_KEYWORDS.put("Boxster", "Boxster");
    MODEL_KEYWORDS.put("Cayman", "Cayman");
    MODEL_KEYWORDS.put("Cooper", "Cooper");
    MODEL_KEYWORDS.put("CX-3", "CX-3");
    MODEL_KEYWORDS.put("CX-5", "CX-5");
    MODEL_KEYWORDS.put("CX-30", "CX-30");
    MODEL_KEYWORDS.put("CX-60", "CX-60");
    MODEL_KEYWORDS.put("Mazda 6", "Mazda 6");
    MODEL_KEYWORDS.put("MX-5", "MX-5");
    MODEL_KEYWORDS.put("Civic", "Civic");
    MODEL_KEYWORDS.put("CR-V", "CR-V");
    MODEL_KEYWORDS.put("HR-V", "HR-V");
    MODEL_KEYWORDS.put("Jazz", "Jazz");
    MODEL_KEYWORDS.put("Accord", "Accord");
    MODEL_KEYWORDS.put("Defender", "Defender");
    MODEL_KEYWORDS.put("Discovery", "Discovery");
    MODEL_KEYWORDS.put("Range Rover", "Range Rover");
    MODEL_KEYWORDS.put("Freelander", "Freelander");
    MODEL_KEYWORDS.put("Giulietta", "Giulietta");
    MODEL_KEYWORDS.put("SL", "SL");
    MODEL_KEYWORDS.put("S7", "S7");
    MODEL_KEYWORDS.put("Seria 1", "1 Series");
    MODEL_KEYWORDS.put("Seria 2", "2 Series");
    MODEL_KEYWORDS.put("Seria 3", "3 Series");
    MODEL_KEYWORDS.put("Seria 4", "4 Series");
    MODEL_KEYWORDS.put("Seria 5", "5 Series");
    MODEL_KEYWORDS.put("Seria 6", "6 Series");
    MODEL_KEYWORDS.put("Seria 7", "7 Series");
    MODEL_KEYWORDS.put("Seria 8", "8 Series");
    MODEL_KEYWORDS.put("116", "1 Series");
    MODEL_KEYWORDS.put("118", "1 Series");
    MODEL_KEYWORDS.put("120", "1 Series");
    MODEL_KEYWORDS.put("F20", "1 Series");
    MODEL_KEYWORDS.put("F21", "1 Series");
    MODEL_KEYWORDS.put("316", "3 Series");
    MODEL_KEYWORDS.put("318", "3 Series");
    MODEL_KEYWORDS.put("320", "3 Series");
    MODEL_KEYWORDS.put("330", "3 Series");
    MODEL_KEYWORDS.put("F30", "3 Series");
    MODEL_KEYWORDS.put("F31", "3 Series");
    MODEL_KEYWORDS.put("F34", "3 Series GT");
    MODEL_KEYWORDS.put("F35", "3 Series");
    MODEL_KEYWORDS.put("G20", "3 Series");
    MODEL_KEYWORDS.put("418", "4 Series");
    MODEL_KEYWORDS.put("420", "4 Series");
    MODEL_KEYWORDS.put("420d", "4 Series");
    MODEL_KEYWORDS.put("430", "4 Series");
    MODEL_KEYWORDS.put("F32", "4 Series");
    MODEL_KEYWORDS.put("F36", "4 Series GC");
    MODEL_KEYWORDS.put("518", "5 Series");
    MODEL_KEYWORDS.put("520", "5 Series");
    MODEL_KEYWORDS.put("520d", "5 Series");
    MODEL_KEYWORDS.put("530", "5 Series");
    MODEL_KEYWORDS.put("535", "5 Series");
    MODEL_KEYWORDS.put("540", "5 Series");
    MODEL_KEYWORDS.put("F10", "5 Series");
    MODEL_KEYWORDS.put("F11", "5 Series");
    MODEL_KEYWORDS.put("G30", "5 Series");
    MODEL_KEYWORDS.put("M550i", "5 Series");
    MODEL_KEYWORDS.put("628", "6 Series");
    MODEL_KEYWORDS.put("630", "6 Series");
    MODEL_KEYWORDS.put("640", "6 Series");
    MODEL_KEYWORDS.put("640i", "6 Series");
    MODEL_KEYWORDS.put("650", "6 Series");
    MODEL_KEYWORDS.put("F06", "6 Series GC");
    MODEL_KEYWORDS.put("F12", "6 Series");
    MODEL_KEYWORDS.put("F13", "6 Series");
    MODEL_KEYWORDS.put("728", "7 Series");
    MODEL_KEYWORDS.put("730", "7 Series");
    MODEL_KEYWORDS.put("740", "7 Series");
    MODEL_KEYWORDS.put("740D", "7 Series");
    MODEL_KEYWORDS.put("750", "7 Series");
    MODEL_KEYWORDS.put("760", "7 Series");
    MODEL_KEYWORDS.put("G", "G-Class");
    MODEL_KEYWORDS.put("C", "C-Class");
    MODEL_KEYWORDS.put("E", "E-Class");
    MODEL_KEYWORDS.put("S", "S-Class");
    MODEL_KEYWORDS.put("A", "A-Class");
    MODEL_KEYWORDS.put("B", "B-Class");
    MODEL_KEYWORDS.put("W124", "W124");
    MODEL_KEYWORDS.put("Cruze", "Cruze");
    MODEL_KEYWORDS.put("Lynk", "Lynk & Co");
    MODEL_KEYWORDS.put("E220d", "E-Class");
    MODEL_KEYWORDS.put("XC 60", "XC60");
  }

  private static final List<Pattern> PATTERNS = MODEL_KEYWORDS.keySet().stream()
      .map(k -> Pattern.compile("(^|[^a-z0-9])" + k.replace(" ", "\\s*") + "($|[^a-z0-9])", Pattern.CASE_INSENSITIVE))
      .toList();

  private ModelNormalizer() {
  }

  public static String normalize(String title) {
    if (title == null || title.isBlank()) return null;
    String t = title.trim();
    for (int i = 0; i < PATTERNS.size(); i++) {
      if (PATTERNS.get(i).matcher(t).find()) {
        return MODEL_KEYWORDS.values().stream().skip(i).findFirst().get();
      }
    }
    String[] parts = t.split("\\s+");
    if (parts.length >= 3) return (parts[1] + " " + parts[2]).trim();
    if (parts.length == 2) return parts[1];
    return parts[0];
  }
}
