package com.aws.carddemo.util;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Input validation utility class migrated from CSLKPCDY.cpy COBOL copybook.
 * Provides comprehensive validation methods for phone numbers, state codes, ZIP codes,
 * email addresses, and credit card numbers using Luhn algorithm.
 * 
 * <p>Migrated from: app/cpy/CSLKPCDY.cpy</p>
 * <p>Original COBOL copybook contained 88-level condition names for North American
 * phone area codes (500+ values), US state codes (50 states + territories), and
 * state-ZIP code combinations for validation purposes.</p>
 * 
 * <p>This utility replaces COBOL 88-level condition name evaluation with Java Set
 * membership checking for O(1) lookup performance.</p>
 * 
 * @since 1.0.0
 */
@Component
public class ValidationUtil {

    /**
     * Set of valid North American Numbering Plan (NANP) area codes.
     * Data obtained from North America Numbering Plan Administrator (NANPA).
     * Includes both general purpose codes and easily recognizable codes (211, 311, 411, etc.).
     * 
     * <p>Replaces COBOL 88-level VALID-PHONE-AREA-CODE condition name.</p>
     */
    private static final Set<String> VALID_AREA_CODES = new HashSet<>();

    /**
     * Set of valid US state codes using two-letter USPS abbreviations.
     * Includes 50 US states, District of Columbia, and 5 US territories.
     * 
     * <p>Replaces COBOL 88-level VALID-US-STATE-CODE condition name.</p>
     */
    private static final Set<String> VALID_STATE_CODES = new HashSet<>();

    /**
     * Map of US state codes to valid ZIP code prefixes (first 2 digits).
     * Used for state-ZIP code combination validation to ensure geographic consistency.
     * 
     * <p>Replaces COBOL 88-level VALID-US-STATE-ZIP-CD2-COMBO condition name.</p>
     */
    private static final Map<String, Set<String>> STATE_ZIP_PREFIX_MAP = new HashMap<>();

    /**
     * Regular expression pattern for validating phone number formats.
     * Accepts formats: (###) ###-#### or ###-###-####
     */
    private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile(
        "^\\(?([0-9]{3})\\)?[\\s.-]?([0-9]{3})[\\s.-]?([0-9]{4})$"
    );

    /**
     * Regular expression pattern for validating email addresses per RFC 5322.
     * Simplified pattern for common email formats.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    /**
     * Regular expression pattern for validating ZIP codes.
     * Accepts 5-digit format (##### ) or 9-digit format (#####-####).
     */
    private static final Pattern ZIP_CODE_PATTERN = Pattern.compile(
        "^[0-9]{5}(?:-[0-9]{4})?$"
    );

    static {
        initializeAreaCodes();
        initializeStateCodes();
        initializeStateZipPrefixMap();
    }

    /**
     * Initializes the set of valid North American phone area codes.
     * Data sourced from NANPA (North American Numbering Plan Administrator).
     */
    private static void initializeAreaCodes() {
        // General purpose area codes from COBOL VALID-PHONE-AREA-CODE
        String[] areaCodes = {
            "201", "202", "203", "204", "205", "206", "207", "208", "209", "210",
            "212", "213", "214", "215", "216", "217", "218", "219", "220", "223",
            "224", "225", "226", "228", "229", "231", "234", "236", "239", "240",
            "242", "246", "248", "249", "250", "251", "252", "253", "254", "256",
            "260", "262", "264", "267", "268", "269", "270", "272", "276", "279",
            "281", "284", "289", "301", "302", "303", "304", "305", "306", "307",
            "308", "309", "310", "312", "313", "314", "315", "316", "317", "318",
            "319", "320", "321", "323", "325", "326", "330", "331", "332", "334",
            "336", "337", "339", "340", "341", "343", "345", "346", "347", "351",
            "352", "360", "361", "364", "365", "367", "368", "380", "385", "386",
            "401", "402", "403", "404", "405", "406", "407", "408", "409", "410",
            "412", "413", "414", "415", "416", "417", "418", "419", "423", "424",
            "425", "430", "431", "432", "434", "435", "437", "438", "440", "441",
            "442", "443", "445", "447", "448", "450", "458", "463", "464", "469",
            "470", "473", "474", "475", "478", "479", "480", "484", "501", "502",
            "503", "504", "505", "506", "507", "508", "509", "510", "512", "513",
            "514", "515", "516", "517", "518", "519", "520", "530", "531", "534",
            "539", "540", "541", "548", "551", "559", "561", "562", "563", "564",
            "567", "570", "571", "572", "573", "574", "575", "579", "580", "581",
            "582", "585", "586", "587", "601", "602", "603", "604", "605", "606",
            "607", "608", "609", "610", "612", "613", "614", "615", "616", "617",
            "618", "619", "620", "623", "626", "628", "629", "630", "631", "636",
            "639", "640", "641", "646", "647", "649", "650", "651", "656", "657",
            "658", "659", "660", "661", "662", "664", "667", "669", "670", "671",
            "672", "678", "680", "681", "682", "683", "684", "689", "701", "702",
            "703", "704", "705", "706", "707", "708", "709", "712", "713", "714",
            "715", "716", "717", "718", "719", "720", "721", "724", "725", "726",
            "727", "731", "732", "734", "737", "740", "742", "743", "747", "753",
            "754", "757", "758", "760", "762", "763", "765", "767", "769", "770",
            "771", "772", "773", "774", "775", "778", "779", "780", "781", "782",
            "784", "785", "786", "787", "801", "802", "803", "804", "805", "806",
            "807", "808", "809", "810", "812", "813", "814", "815", "816", "817",
            "818", "819", "820", "825", "826", "828", "829", "830", "831", "832",
            "838", "839", "840", "843", "845", "847", "848", "849", "850", "854",
            "856", "857", "858", "859", "860", "862", "863", "864", "865", "867",
            "868", "869", "870", "872", "873", "876", "878", "901", "902", "903",
            "904", "905", "906", "907", "908", "909", "910", "912", "913", "914",
            "915", "916", "917", "918", "919", "920", "925", "928", "929", "930",
            "931", "934", "936", "937", "938", "939", "940", "941", "943", "945",
            "947", "948", "949", "951", "952", "954", "956", "959", "970", "971",
            "972", "973", "978", "979", "980", "983", "984", "985", "986", "989",
            // Easily recognizable codes (211, 311, 411, 511, 611, 711, 811, 911)
            "200", "211", "222", "233", "244", "255", "266", "277", "288", "299",
            "300", "311", "322", "333", "344", "355", "366", "377", "388", "399",
            "400", "411", "422", "433", "444", "455", "466", "477", "488", "499",
            "500", "511", "522", "533", "544", "555", "566", "577", "588", "599",
            "600", "611", "622", "633", "644", "655", "666", "677", "688", "699",
            "700", "711", "722", "733", "744", "755", "766", "777", "788", "799",
            "800", "811", "822", "833", "844", "855", "866", "877", "888", "899",
            "900", "911", "922", "933", "944", "955", "966", "977", "988", "999"
        };
        
        for (String areaCode : areaCodes) {
            VALID_AREA_CODES.add(areaCode);
        }
    }

    /**
     * Initializes the set of valid US state codes.
     * Includes 50 US states, District of Columbia, and US territories.
     */
    private static void initializeStateCodes() {
        // US state codes from COBOL VALID-US-STATE-CODE
        String[] stateCodes = {
            "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
            "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
            "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
            "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
            "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY",
            "DC", "AS", "GU", "MP", "PR", "VI"
        };
        
        for (String stateCode : stateCodes) {
            VALID_STATE_CODES.add(stateCode);
        }
    }

    /**
     * Initializes the state-to-ZIP-prefix mapping for validation.
     * Maps each state code to its valid ZIP code prefixes (first 2 digits).
     */
    private static void initializeStateZipPrefixMap() {
        // State-ZIP prefix combinations from COBOL VALID-US-STATE-ZIP-CD2-COMBO
        addStateZipPrefix("AA", "34");
        addStateZipPrefix("AE", "90", "91", "92", "93", "94", "95", "96", "97", "98");
        addStateZipPrefix("AK", "99");
        addStateZipPrefix("AL", "35", "36");
        addStateZipPrefix("AP", "96");
        addStateZipPrefix("AR", "71", "72");
        addStateZipPrefix("AS", "96");
        addStateZipPrefix("AZ", "85", "86");
        addStateZipPrefix("CA", "90", "91", "92", "93", "94", "95", "96");
        addStateZipPrefix("CO", "80", "81");
        addStateZipPrefix("CT", "60", "61", "62", "63", "64", "65", "66", "67", "68", "69");
        addStateZipPrefix("DC", "20", "56", "88");
        addStateZipPrefix("DE", "19");
        addStateZipPrefix("FL", "32", "33", "34");
        addStateZipPrefix("FM", "96");
        addStateZipPrefix("GA", "30", "31", "39");
        addStateZipPrefix("GU", "96");
        addStateZipPrefix("HI", "96");
        addStateZipPrefix("IA", "50", "51", "52");
        addStateZipPrefix("ID", "83");
        addStateZipPrefix("IL", "60", "61", "62");
        addStateZipPrefix("IN", "46", "47");
        addStateZipPrefix("KS", "66", "67");
        addStateZipPrefix("KY", "40", "41", "42");
        addStateZipPrefix("LA", "70", "71");
        addStateZipPrefix("MA", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", 
                         "20", "21", "22", "23", "24", "25", "26", "27", "55");
        addStateZipPrefix("MD", "20", "21");
        addStateZipPrefix("ME", "39", "40", "41", "42", "43", "44", "45", "46", "47", "48", "49");
        addStateZipPrefix("MH", "96");
        addStateZipPrefix("MI", "48", "49");
        addStateZipPrefix("MN", "55", "56");
        addStateZipPrefix("MO", "63", "64", "65", "72");
        addStateZipPrefix("MP", "96");
        addStateZipPrefix("MS", "38", "39");
        addStateZipPrefix("MT", "59");
        addStateZipPrefix("NC", "27", "28");
        addStateZipPrefix("ND", "58");
        addStateZipPrefix("NE", "68", "69");
        addStateZipPrefix("NH", "30", "31", "32", "33", "34", "35", "36", "37", "38");
        addStateZipPrefix("NJ", "70", "71", "72", "73", "74", "75", "76", "77", "78", "79",
                         "80", "81", "82", "83", "84", "85", "86", "87", "88", "89");
        addStateZipPrefix("NM", "87", "88");
        addStateZipPrefix("NV", "88", "89");
        addStateZipPrefix("NY", "10", "11", "12", "13", "14", "50", "54", "63");
        addStateZipPrefix("OH", "43", "44", "45");
        addStateZipPrefix("OK", "73", "74");
        addStateZipPrefix("OR", "97");
        addStateZipPrefix("PA", "15", "16", "17", "18", "19");
        addStateZipPrefix("PR", "60", "61", "62", "63", "64", "65", "66", "67", "68", "69",
                         "70", "71", "72", "73", "74", "75", "76", "77", "78", "79",
                         "90", "91", "92", "93", "94", "95", "96", "97", "98");
        addStateZipPrefix("PW", "96");
        addStateZipPrefix("RI", "28", "29");
        addStateZipPrefix("SC", "29");
        addStateZipPrefix("SD", "57");
        addStateZipPrefix("TN", "37", "38");
        addStateZipPrefix("TX", "73", "75", "76", "77", "78", "79", "88");
        addStateZipPrefix("UT", "84");
        addStateZipPrefix("VA", "20", "22", "23", "24");
        addStateZipPrefix("VI", "80", "82", "83", "84", "85");
        addStateZipPrefix("VT", "50", "51", "52", "53", "54", "56", "57", "58", "59");
        addStateZipPrefix("WA", "98", "99");
        addStateZipPrefix("WI", "53", "54");
        addStateZipPrefix("WV", "24", "25", "26");
        addStateZipPrefix("WY", "82", "83");
    }

    /**
     * Helper method to add state-ZIP prefix associations.
     * 
     * @param state the two-letter state code
     * @param prefixes variable number of two-digit ZIP prefixes
     */
    private static void addStateZipPrefix(String state, String... prefixes) {
        Set<String> prefixSet = STATE_ZIP_PREFIX_MAP.computeIfAbsent(state, k -> new HashSet<>());
        for (String prefix : prefixes) {
            prefixSet.add(prefix);
        }
    }

    /**
     * Validates a phone area code against the North American Numbering Plan.
     * 
     * <p>Replaces COBOL 88-level condition: VALID-PHONE-AREA-CODE</p>
     * 
     * <p>Example valid area codes: "212" (New York), "415" (San Francisco), 
     * "555" (reserved for fiction), "800" (toll-free)</p>
     * 
     * @param areaCode the three-digit area code to validate (case-insensitive)
     * @return true if the area code is valid per NANP, false otherwise
     */
    public boolean isValidPhoneAreaCode(String areaCode) {
        if (areaCode == null || areaCode.trim().isEmpty()) {
            return false;
        }
        return VALID_AREA_CODES.contains(areaCode.trim());
    }

    /**
     * Validates a US state code against USPS two-letter abbreviations.
     * 
     * <p>Replaces COBOL 88-level condition: VALID-US-STATE-CODE</p>
     * 
     * <p>Includes 50 US states, District of Columbia, and 5 US territories 
     * (AS, GU, MP, PR, VI).</p>
     * 
     * <p>Example valid codes: "CA" (California), "TX" (Texas), "NY" (New York),
     * "DC" (District of Columbia), "PR" (Puerto Rico)</p>
     * 
     * @param stateCode the two-letter state code to validate (case-insensitive)
     * @return true if the state code is valid, false otherwise
     */
    public boolean isValidStateCode(String stateCode) {
        if (stateCode == null || stateCode.trim().isEmpty()) {
            return false;
        }
        return VALID_STATE_CODES.contains(stateCode.trim().toUpperCase(Locale.US));
    }

    /**
     * Validates a state-ZIP code combination for geographic consistency.
     * Ensures that the ZIP code prefix matches the state's valid ZIP ranges.
     * 
     * <p>Replaces COBOL 88-level condition: VALID-US-STATE-ZIP-CD2-COMBO</p>
     * 
     * <p>Example valid combinations:
     * <ul>
     *   <li>CA + 90210 → true (California ZIP starts with 90-96)</li>
     *   <li>NY + 10001 → true (New York ZIP starts with 10-14)</li>
     *   <li>TX + 90210 → false (Texas ZIP doesn't start with 90)</li>
     * </ul>
     * </p>
     * 
     * @param stateCode the two-letter state code (case-insensitive)
     * @param zipCode the 5-digit or 9-digit ZIP code
     * @return true if the state-ZIP combination is geographically valid, false otherwise
     */
    public boolean isValidStateZipCombo(String stateCode, String zipCode) {
        if (stateCode == null || zipCode == null || 
            stateCode.trim().isEmpty() || zipCode.trim().isEmpty()) {
            return false;
        }
        
        String normalizedState = stateCode.trim().toUpperCase(Locale.US);
        String normalizedZip = zipCode.trim();
        
        // Extract first 2 digits of ZIP code
        if (normalizedZip.length() < 2) {
            return false;
        }
        
        String zipPrefix = normalizedZip.substring(0, 2);
        
        Set<String> validPrefixes = STATE_ZIP_PREFIX_MAP.get(normalizedState);
        if (validPrefixes == null) {
            return false;
        }
        
        return validPrefixes.contains(zipPrefix);
    }

    /**
     * Validates a complete phone number format with area code validation.
     * Accepts formats: (###) ###-####, ###-###-####, or ### ### ####
     * 
     * <p>Performs both format validation and area code validation against NANP.</p>
     * 
     * <p>Example valid phone numbers:
     * <ul>
     *   <li>(212) 555-1234</li>
     *   <li>415-555-9876</li>
     *   <li>800 555 0199</li>
     * </ul>
     * </p>
     * 
     * @param phoneNumber the phone number string to validate
     * @return true if the phone number format is valid and area code is recognized, false otherwise
     */
    public boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        
        Matcher matcher = PHONE_NUMBER_PATTERN.matcher(phoneNumber.trim());
        if (!matcher.matches()) {
            return false;
        }
        
        // Extract and validate area code
        String areaCode = matcher.group(1);
        return isValidPhoneAreaCode(areaCode);
    }

    /**
     * Validates an email address format per RFC 5322 simplified pattern.
     * 
     * <p>Checks for valid email structure: localpart@domain.tld</p>
     * 
     * <p>Example valid emails:
     * <ul>
     *   <li>user@example.com</li>
     *   <li>john.doe+tag@company.co.uk</li>
     *   <li>admin_123@test-domain.org</li>
     * </ul>
     * </p>
     * 
     * @param email the email address to validate
     * @return true if the email format is valid, false otherwise
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        Matcher matcher = EMAIL_PATTERN.matcher(email.trim());
        return matcher.matches();
    }

    /**
     * Validates a credit card number using the Luhn algorithm (modulus 10).
     * 
     * <p>The Luhn algorithm verifies card number integrity by:
     * <ol>
     *   <li>Starting from the rightmost digit, double every second digit</li>
     *   <li>If doubled digit > 9, subtract 9 from the result</li>
     *   <li>Sum all digits</li>
     *   <li>Number is valid if sum modulo 10 equals 0</li>
     * </ol>
     * </p>
     * 
     * <p>Example valid card numbers (test cards):
     * <ul>
     *   <li>4532015112830366 (Visa test card)</li>
     *   <li>5425233430109903 (Mastercard test card)</li>
     *   <li>374245455400126 (Amex test card)</li>
     * </ul>
     * </p>
     * 
     * <p>Note: This validates card number integrity only, not card validity or issuer.</p>
     * 
     * @param cardNumber the credit card number to validate (digits only, 13-19 digits)
     * @return true if the card number passes Luhn checksum validation, false otherwise
     */
    public boolean isValidCreditCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            return false;
        }
        
        String digits = cardNumber.trim().replaceAll("[^0-9]", "");
        
        // Credit card numbers are typically 13-19 digits
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        
        // Luhn algorithm implementation
        int sum = 0;
        boolean alternate = false;
        
        // Process digits from right to left
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(digits.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        // Valid if sum is divisible by 10
        return (sum % 10 == 0);
    }

    /**
     * Validates a ZIP code format for 5-digit or 9-digit (ZIP+4) formats.
     * 
     * <p>Accepted formats:
     * <ul>
     *   <li>5-digit: 12345</li>
     *   <li>9-digit (ZIP+4): 12345-6789</li>
     * </ul>
     * </p>
     * 
     * <p>Note: This validates format only. For geographic validation, use 
     * {@link #isValidStateZipCombo(String, String)} in combination.</p>
     * 
     * @param zipCode the ZIP code to validate
     * @return true if the ZIP code format is valid, false otherwise
     */
    public boolean isValidZipCode(String zipCode) {
        if (zipCode == null || zipCode.trim().isEmpty()) {
            return false;
        }
        
        Matcher matcher = ZIP_CODE_PATTERN.matcher(zipCode.trim());
        return matcher.matches();
    }
}

