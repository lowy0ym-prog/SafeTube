/*
 * SafeTube - based on SkyTube (GPLv3). Whitelist-only modifications.
 */
package free.rm.skytube.businessobjects.whitelist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Men only" screening of video metadata (title + description).
 *
 * IMPORTANT LIMITS: this only reads text. It cannot see or hear a video. It is deliberately
 * one-directional: it can hide too much, and it needs positive evidence before a video from a
 * "strict" channel is shown. A woman who is not named or mentioned in the text can still appear.
 */
public final class Screening {
    private Screening() { }

    public enum Verdict { ALLOW, BLOCK, UNKNOWN }

    private static Set<String> set(String... v) {
        return new HashSet<>(Arrays.asList(v));
    }

    /** Words that indicate a woman or girl is mentioned. ("ms" is left out: it means milliseconds.) */
    static final Set<String> FEMALE_WORDS = set(
            "she", "her", "hers", "herself", "woman", "women", "womens", "woman's", "women's",
            "girl", "girls", "lady", "ladies", "female", "females", "mrs", "madam", "mother", "mom",
            "wife", "daughter", "sister", "actress", "feminine", "gal", "gals", "girlfriend",
            "bride", "aunt", "grandmother", "grandma", "niece", "waitress", "businesswoman",
            "chairwoman", "femme");

    /** Podcast / interview / talk style videos: a guest's identity cannot be verified from text. */
    static final String[] GUEST_TERMS = {
            "interview", "podcast", "conversation", "panel", "fireside", "q&a", " ama ", "guest",
            "featuring", " ft.", " feat.", "live stream", "livestream", "talk with", "chat with",
            "discussion", "roundtable", "keynote", "conference", "meetup", "in conversation",
            "speaks", "story of"
    };

    static final Set<String> FEMALE_NAMES = set(
            "estefania", "kylie", "tiffany", "madison", "vanessa", "sara", "sarah", "mary", "anna", "emma",
            "olivia", "sophia", "isabella", "ava", "mia", "emily", "abigail", "charlotte", "amelia", "harper",
            "evelyn", "elizabeth", "sofia", "ella", "avery", "scarlett", "victoria", "aria", "grace",
            "chloe", "camila", "penelope", "layla", "riley", "lillian", "nora", "zoey", "mila", "hannah",
            "lily", "eleanor", "addison", "aubrey", "ellie", "stella", "natalie", "zoe", "leah", "hazel",
            "violet", "aurora", "savannah", "audrey", "brooklyn", "bella", "claire", "skylar", "lucy",
            "paisley", "everly", "anna", "caroline", "nova", "genesis", "emilia", "kennedy", "maya",
            "willow", "kinsley", "naomi", "aaliyah", "elena", "sarai", "ariana", "allison", "gabriella",
            "alice", "madelyn", "cora", "ruby", "eva", "serenity", "autumn", "adeline", "hailey", "gianna",
            "valentina", "isla", "eliana", "quinn", "nevaeh", "ivy", "sadie", "piper", "lydia", "alexa",
            "josephine", "emery", "julia", "delilah", "arianna", "vivian", "kaylee", "sophie", "brielle",
            "madeline", "peyton", "rylee", "clara", "hadley", "melanie", "mackenzie", "reagan", "adalynn",
            "liliana", "aubree", "jade", "katherine", "isabelle", "natalia", "raelynn", "maria", "athena",
            "ximena", "arya", "leilani", "taylor", "faith", "rose", "alexandra", "mary", "margaret",
            "lyla", "ashley", "amaya", "eliza", "brianna", "bailey", "andrea", "khloe", "jasmine",
            "melody", "iris", "isabel", "norah", "annabelle", "valeria", "emerson", "adalyn", "ryleigh",
            "eden", "emilyn", "anastasia", "kayla", "alyssa", "juliana", "charlie", "esther", "ariel",
            "cecilia", "valerie", "alina", "molly", "reese", "aliyah", "lilliana", "rachel", "amy",
            "laura", "jessica", "jennifer", "linda", "patricia", "susan", "karen", "nancy", "lisa",
            "betty", "dorothy", "sandra", "donna", "carol", "michelle", "kimberly", "deborah", "stephanie",
            "cynthia", "angela", "melissa", "brenda", "pamela", "nicole", "katie", "samantha", "christine",
            "catherine", "debra", "janet", "carolyn", "heather", "diane", "olga", "julie", "joyce",
            "virginia", "kelly", "lauren", "joan", "megan", "teresa", "cheryl", "gloria", "judith",
            "rebecca", "kathleen", "tina", "shirley", "angelina", "priya", "anita", "sunita", "neha",
            "pooja", "divya", "kavita", "shruti", "swati", "sneha", "ananya", "aisha", "fatima", "zainab",
            "yuki", "mei", "lin", "wei", "ling", "xiao", "hana", "sakura", "natasha", "svetlana", "elena",
            "irina", "tatiana", "ekaterina", "marina", "daria", "oksana", "lena", "ingrid", "astrid",
            "freya", "greta", "heidi", "monica", "paula", "carla", "lucia", "sofie", "camille", "claudia",
            "beth", "kate", "jenny", "jen", "jess", "becky", "amanda", "ada", "cassidy", "tabitha",
            "cassie", "jane", "gwen", "gwendolyn", "lorna", "wendy", "tracy", "tracey", "joanna",
            "bianca", "elisa", "elisabeth", "leila", "nadia", "yasmin", "zara", "sana", "hira", "maham");

    static final Set<String> MALE_NAMES = set(
            "james", "john", "robert", "michael", "william", "david", "richard", "joseph", "thomas", "charles",
            "christopher", "daniel", "matthew", "anthony", "mark", "donald", "steven", "paul", "andrew",
            "joshua", "kenneth", "kevin", "brian", "george", "timothy", "ronald", "edward", "jason", "jeffrey",
            "ryan", "jacob", "gary", "nicholas", "eric", "jonathan", "stephen", "larry", "justin", "scott",
            "brandon", "benjamin", "samuel", "gregory", "alexander", "patrick", "frank", "raymond", "jack",
            "dennis", "jerry", "tyler", "aaron", "adam", "nathan", "henry", "peter", "zachary",
            "douglas", "harold", "kyle", "carl", "arthur", "gerald", "roger", "keith", "jeremy", "terry",
            "lawrence", "sean", "christian", "albert", "joe", "ethan", "austin", "billy",
            "bryan", "bruce", "ralph", "roy", "noah", "dylan", "eugene", "wayne", "alan", "juan",
            "louis", "russell", "gabriel", "randy", "philip", "harry", "vincent", "bobby", "johnny",
            "logan", "colt", "beau", "quincy", "dave", "mike", "tom", "tim", "bob", "bill", "jim", "joel",
            "ben", "dan", "matt", "chris", "rob", "steve", "tony", "andy", "luke", "leo", "max",
            "oliver", "liam", "mason", "elijah", "lucas", "oscar", "felix", "hugo", "victor", "marco",
            "carlos", "miguel", "pedro", "luis", "diego", "javier", "mohammed", "muhammad", "ahmed", "ali",
            "omar", "hassan", "hussein", "ibrahim", "yusuf", "bilal", "usman", "hamza", "imran", "rahul",
            "amit", "raj", "vijay", "sanjay", "ravi", "anil", "sunil", "rohit", "arjun", "karan", "nikhil",
            "hitesh", "harkirat", "kunal", "manish", "mosh", "brad", "corey",
            "wes", "kent", "kyle", "ed", "ted", "fred", "ray", "jay", "pete", "rick",
            "ian", "neil", "evan", "glenn", "greg", "craig", "shawn", "todd", "derek", "dustin", "grant",
            "hans", "klaus", "stefan", "lars", "sven", "pavel", "ivan", "dmitry", "sergey", "alexey",
            "vladimir", "andrei", "mikhail", "nikolai", "yuri", "oleg", "igor", "maxim", "artem", "denis",
            "hiro", "takashi", "kenji", "hiroshi", "tarun", "shivam",
            "abdul", "ahmad", "bassem", "tariq", "walid", "khalid", "faisal", "zain", "ryan", "ross", "dean",
            "colin", "gavin", "graham", "stuart", "malcolm", "duncan", "angus", "hamish", "rory", "cameron");

    private static final Pattern WORD = Pattern.compile("[a-z][a-z'/]*");
    private static final Pattern CAP_PAIR = Pattern.compile("\\b([A-Z][a-z]{2,})\\s+[A-Z][a-zA-Z'\\-]{1,}");
    private static final Pattern CREDIT = Pattern.compile(
            "(?i)(?:created|developed|taught|made|presented|produced|written|designed|instructed|hosted|"
                    + "built|authored)\\s+by\\s+([^\\n.!?]{2,200})|(?:instructor|teacher|presenter|host|author|"
                    + "tutor|lecturer)s?\\s*[:\\-]\\s*([^\\n.!?]{2,200})");

    private static final Pattern GUEST_LINE = Pattern.compile(
            "(?:\\bwith|featuring|\\bfeat\\.?|\\bft\\.?|\\bguests?|joined by|interview(?:ing)? with|hosted by|"
                    + "speaker|speakers)\\s*:?\\s+(?:dr\\.?\\s+|prof\\.?\\s+)?([A-Z][a-z]{2,})");

    /** Female given names that appear right after a "with / featuring / guest / hosted by" cue. */
    static boolean femaleNameInCredits(String text) {
        if (text == null) return false;
        String head = text.length() > 3000 ? text.substring(0, 3000) : text;
        Matcher g = GUEST_LINE.matcher(head);
        while (g.find()) {
            if (FEMALE_NAMES.contains(g.group(1).toLowerCase(Locale.ROOT))) return true;
        }
        for (String n : creditedFirstNames(head)) {
            if (FEMALE_NAMES.contains(n)) return true;
        }
        return false;
    }

    public static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /** True if the text mentions a woman/girl by word, or names someone with a female given name. */
    public static boolean mentionsFemale(String text) {
        if (text == null || text.isEmpty()) return false;
        for (String w : words(text)) {
            if (FEMALE_WORDS.contains(w)) return true;
            if (w.contains("/") && (w.startsWith("she") || w.contains("/she") || w.contains("her"))) return true;
        }
        String head = text.length() > 1500 ? text.substring(0, 1500) : text;
        Matcher m = CAP_PAIR.matcher(head);
        while (m.find()) {
            if (FEMALE_NAMES.contains(m.group(1).toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public static boolean looksLikeGuestFormat(String text) {
        if (text == null) return false;
        String t = " " + text.toLowerCase(Locale.ROOT) + " ";
        for (String g : GUEST_TERMS) {
            if (t.contains(g)) return true;
        }
        return false;
    }

    /** First names of the people credited as creators/instructors in the description. */
    public static List<String> creditedFirstNames(String description) {
        List<String> names = new ArrayList<>();
        if (description == null) return names;
        Matcher m = CREDIT.matcher(description);
        while (m.find()) {
            String seg = m.group(1) != null ? m.group(1) : m.group(2);
            seg = seg.split("(?i)\\s+(?:on|from|for|at|of|who|with|in|\\(|http)\\b|\\(|http", 2)[0];
            for (String part : seg.split("(?i)\\s*(?:,|;|&|\\band\\b)\\s*")) {
                String[] toks = part.trim().split("\\s+");
                for (String t : toks) {
                    String clean = t.replaceAll("[^A-Za-z]", "");
                    if (clean.isEmpty()) continue;
                    String lower = clean.toLowerCase(Locale.ROOT);
                    if (lower.equals("dr") || lower.equals("prof") || lower.equals("professor") || lower.equals("mr")) continue;
                    if (Character.isUpperCase(clean.charAt(0))) names.add(lower);
                    break; // only the first real token (the first name)
                }
            }
        }
        return names;
    }

    /**
     * Classify one video.
     *
     * @param strict false (default): BLOCK only on clear evidence (title mentions a woman, or a
     *               female name is credited as instructor/guest/host), otherwise ALLOW.
     *               true : additionally ALLOW only with positive evidence (credited, clearly male
     *               first names, no guest/interview format); otherwise UNKNOWN (which hides it).
     */
    public static Verdict classify(String title, String description, boolean strict) {
        String all = (title == null ? "" : title) + "\n" + (description == null ? "" : description);
        if (!strict) {
            // Default: block only on clear evidence. A woman named or described in the title, or a
            // female given name credited as instructor / guest / host in the description.
            if (mentionsFemale(title)) return Verdict.BLOCK;
            if (femaleNameInCredits(description)) return Verdict.BLOCK;
            return Verdict.ALLOW;
        }
        if (mentionsFemale(all)) return Verdict.BLOCK;
        if (looksLikeGuestFormat(all)) return Verdict.BLOCK;
        List<String> names = creditedFirstNames(description);
        if (names.isEmpty()) return Verdict.UNKNOWN;
        for (String n : names) {
            if (FEMALE_NAMES.contains(n)) return Verdict.BLOCK;
            if (!MALE_NAMES.contains(n)) return Verdict.UNKNOWN;
        }
        return Verdict.ALLOW;
    }
}
