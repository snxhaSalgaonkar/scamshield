package com.scamguard.app.collectors

import com.scamguard.app.bus.SignalEvent
import com.scamguard.app.bus.VoiceCategory
import java.text.Normalizer

/**
 * Recognises scam tactics in spoken text (a speech-to-text transcript of a call, or a demo
 * script line): English, Hinglish and Hindi in Devanagari. Pure Kotlin so it is unit tested.
 *
 * Text is matched clause by clause, so context in the same clause can cancel a match:
 * protective advice ("never share your OTP with anyone", "OTP kisi ko mat batana") and a
 * delivery agent asking for the delivery OTP are not requests. Callers keep the categories
 * (and at most the short matched phrase for display) and drop the text.
 */
object SpeechText {

    /** One tactic found in the text, with the words that matched. */
    data class Match(val category: VoiceCategory, val phrase: String)

    private val TERMS: Map<VoiceCategory, List<String>> = mapOf(
        VoiceCategory.AUTHORITY_CLAIM to listOf(
            "cbi", "c b i", "central bureau of investigation", "cyber ?cell", "cyber ?crime", "crime branch",
            "police", "pulis", "enforcement directorate", "ed (office|officer|department)", "narcotics", "ncb",
            "customs (department|officer|office)", "trai", "telecom regulatory", "income tax (department|officer)",
            "reserve bank of india", "rbi (officer|department|official)", "sub inspector", "inspector", "dcp",
            "ips officer", "commissioner", "investigating officer", "interpol", "nia", "national investigation agency",
            "ministry of home affairs", "home ministry", "judge", "magistrate",
            "पुलिस", "सीबीआई", "साइबर (सेल|क्राइम)", "क्राइम ब्रांच", "इंस्पेक्टर",
            "सब इंस्पेक्टर", "ईडी", "प्रवर्तन निदेशालय", "नारकोटिक्स", "कस्टम (विभाग|अधिकारी|ऑफिस)", "ट्राई",
            "दूरसंचार विभाग", "आयकर विभाग", "इनकम टैक्स (विभाग|ऑफिसर)", "रिज़र्व बैंक", "रिजर्व बैंक",
            "आरबीआई (अधिकारी|ऑफिसर|विभाग)", "थाने से", "थानेदार", "डीसीपी", "कमिश्नर", "आईपीएस", "जांच अधिकारी",
            "मजिस्ट्रेट", "जज", "कोर्ट से", "गृह मंत्रालय",
            // Bank staff impersonation (KYC / OTP theft). A claim alone is never a red flag; with an
            // OTP, card or screen-sharing request it is (VoiceRules credential + authority).
            "(calling|call|speaking|phone) (you )?from (the |your )?(\\w+ )?(\\w+ )?bank",
            "(sbi|hdfc|icici|axis|kotak|pnb|canara|union|bank of baroda|bob|yes|idfc|indusind) bank (se|from|head office|customer care|team|department)",
            "bank (head office|manager|officer|official|executive|security|fraud|kyc|verification|customer care|customer service) ?(team|department|cell)?",
            "kyc (department|team|cell|verification team|update team)", "bank se (bol|call|phone)\\w*",
            "बैंक से (बोल|कॉल|फोन)\\w*", "बैंक (मैनेजर|अधिकारी|हेड ऑफिस|कस्टमर केयर)", "केवाईसी (विभाग|टीम)",
        ),
        VoiceCategory.ACCUSATION to listOf(
            "illegal (items?|parcel|goods|drugs|substances?)", "drugs", "mdma", "contraband", "money laundering",
            "hawala", "terror funding", "human trafficking", "fake passports?",
            "(fir|case|complaint) (has been |is |was )?(registered|filed|lodged|darj)", "registered (a |an )?(fir|case)",
            "aadhaa?r( card)? (is |has been |was )?(misused|being misused|used in|linked to)", "aadhaa?r misuse",
            "(sim|sim card|mobile number|number|bank account|account) (is |has been |was )?(used|involved|linked) (in|for|with) (illegal|criminal|fraud|crime|scam)\\w*",
            "(you are|aap) (involved|accused|a suspect)", "accused", "seized", "pakda gaya",
            "ड्रग्स", "मनी लॉन्ड्रिंग",
            "नशीले पदार्थ", "अवैध (सामान|पार्सल|वस्तुएं|चीजें)", "गैर ?कानूनी (सामान|पार्सल|काम)", "हवाला", "टेरर फंडिंग",
            "आतंकी फंडिंग", "मानव तस्करी", "नकली पासपोर्ट",
            "(एफआईआर|केस|मुकदमा|शिकायत) (दर्ज|रजिस्टर)\\w*",
            "आधार( कार्ड)?( का)? (दुरुपयोग|गलत इस्तेमाल|गलत उपयोग)",
            "आपके नाम (पर|से)( एक)? (पार्सल|केस|सिम|एफआईआर|बैंक खाता|अकाउंट)",
            "आप (शामिल|आरोपी|संदिग्ध) (हैं|हो)", "आरोपी", "जब्त", "पकड़ा गया", "पकड़ी गई",
        ),
        VoiceCategory.ARREST_THREAT to listOf(
            "digital(ly)? arrest(ed|ing)?", "arrest warrant", "warrant", "under arrest", "arrest(ed|ing)?",
            "arrest (kar|ho)\\w*", "giraft?aa?r\\w*", "jail", "lock ?up", "non ?bailable",
            "police (will|is going to) come",
            "गिरफ्तार\\w*", "डिजिटल अरेस्ट", "वारंट", "जेल",
            "डिजिटल गिरफ्तारी", "अरेस्ट (कर|हो)\\w*", "हिरासत", "हवालात", "लॉक ?अप", "गैर ?जमानती",
            "पुलिस( आपके घर)? (आ|पहुंच)(\\w*) (जाएगी|रही)", "सजा (होगी|हो सकती)",
        ),
        VoiceCategory.ISOLATION to listOf(
            "(dont|do not|never) (disconnect|hang up|cut|end)( the| this)?( call| phone)?",
            "(phone|call) (mat|na|nahi) (kaat|kat|band|rakh)\\w*",
            "(dont|do not|never) (tell|inform|talk to|discuss (this )?with)( \\w+){0,3} (anyone|anybody|family|parents|wife|husband|friends|relatives|bank|police)",
            "(kisi|ghar|family|parivaar|gharwalon)( \\w+){0,2} (ko|se) (mat|na|nahi|kuch nahi) (bata|bol|keh)\\w*",
            "keep (your |the )?(camera|video) on", "stay on (the |this )?(video )?call", "(camera|video) (on rakh|band mat)\\w*",
            "(go|stay|sit) (alone|in a room)", "lock (the |your )?(door|room)", "akele", "confidential",
            "secret (matter|investigation|operation)", "national security", "official secrets",
            "फोन मत काट\\w*", "किसी को (मत|ना) बता\\w*",
            "(फोन|कॉल) (मत|ना|नहीं) (काट|कट|बंद|रख|डिस्कनेक्ट)\\w*", "(फोन|कॉल) डिस्कनेक्ट (मत|ना|नहीं)",
            "किसी को (भी )?(मत|ना|नहीं) बता\\w*",
            "(घर|परिवार|घरवालों|घर वालों|फैमिली|किसी|पत्नी|पति|दोस्तों)( \\S+){0,2} (को|से) (भी )?(मत|ना|नहीं|कुछ नहीं|कुछ भी नहीं) (बता|बोल|कह)\\w*",
            "(कैमरा|वीडियो) (ऑन|चालू) (रख|रखें|रखिए|रखना)\\w*", "अकेले (में|रह|बैठ)\\w*", "कमरा (बंद|लॉक) (कर|करो|करें)\\w*",
            "गोपनीय (मामला|जांच)", "राष्ट्रीय सुरक्षा",
        ),
        VoiceCategory.PAYMENT_PRESSURE to listOf(
            "transfer( \\w+){0,3} (money|amount|funds|savings|paise|rupees|balance)",
            "(money|amount|funds|savings|paise|paisa|balance)( \\w+){0,4} transfer", "transfer (kar|karo|karein|kijiye)\\w*",
            "safe account", "secure account", "rbi account", "government account", "verification (account|amount)",
            "security deposit", "refundable", "bail amount", "clearance (fee|amount|charges?)",
            "pay (the |a )?(fine|penalty|fee|charges)", "send (me )?(the )?money", "paise (bhej|daal|jama)\\w*",
            "(jama|deposit) (kar|karo|karein|kijiye)\\w*", "upi id", "scan (the |this )?qr", "break (your )?(fd|fixed deposit)",
            "pay (now|immediately)",
            // Amounts: "send me 10000", "pay rs 5,000", "50000 rupees", "das hazaar bhejo".
            "(send|pay|transfer|give)( me| us)?( rs| rupees| inr| ₹)? ?₹?\\d[\\d,]*( rupees| rs)?",
            "\\d[\\d,]* (rupees|rs|rupaye)", "(hazaar|hazar|lakh|lac|crore)( rupaye| rupees)? (bhej|de|transfer|jama)\\w*",
            "पैसे (ट्रांसफर|भेज|जमा)\\w*", "सेफ अकाउंट",
            "(पैसे|पैसा|रकम|राशि|अमाउंट|बैलेंस|सेविंग्स)( \\S+){0,3} (ट्रांसफर|भेज|जमा|डाल)\\w*",
            "ट्रांसफर (कर|करो|करें|कीजिए|करिए)\\w*", "सुरक्षित (खाते|खाता|अकाउंट)", "आरबीआई (के )?(खाते|अकाउंट)",
            "सरकारी (खाते|खाता|अकाउंट)", "वेरिफिकेशन (के लिए )?(पैसे|अमाउंट|राशि)", "सिक्योरिटी डिपॉजिट",
            "जमानत (की )?(राशि|के पैसे)", "(जुर्माना|फाइन|पेनल्टी) (भर|दे|जमा)\\w*", "यूपीआई आईडी",
            "क्यूआर (कोड )?स्कैन", "एफडी तोड़\\w*", "\\d[\\d,]* (रुपये|रुपए|रूपये)",
            "(हजार|हज़ार|लाख|करोड़)( रुपये| रुपए)? (भेज|दे|ट्रांसफर|जमा)\\w*", "अभी (भुगतान|पेमेंट) (कर|करें|करो)\\w*",
        ),
        VoiceCategory.CARD_DETAILS to listOf(
            "card number", "(credit|debit|atm) card (details|number|no)", "cvv", "expiry date",
            "(atm|upi|card|secret) pin", "pin (number|code)", "m ?pin", "net ?banking (password|id|login)",
            "(login|bank|account) password", "card ke (peeche|piche)", "16 digit", "सीवीवी", "पिन",
            "कार्ड (नंबर|का नंबर|की डिटेल\\w*)", "(क्रेडिट|डेबिट|एटीएम) कार्ड (का )?(नंबर|डिटेल\\w*|विवरण)",
            "एक्सपायरी", "(बैंक|लॉगिन|अकाउंट|नेट ?बैंकिंग) (का )?पासवर्ड", "कार्ड के पीछे", "16 अंक",
        ),
        VoiceCategory.REMOTE_APP_REQUEST to listOf(
            "any ?desk", "team ?viewer", "rust ?desk", "quick ?support", "airdroid", "ammyy",
            "screen ?shar\\w*", "share (your |the )?screen", "remote (access|support|app)", "एनीडेस्क",
            "एनी ?डेस्क", "टीम ?व्यूअर", "रस्ट ?डेस्क", "क्विक ?सपोर्ट", "स्क्रीन (शेयर|शेयरिंग)\\w*", "रिमोट (एक्सेस|सपोर्ट|ऐप)",
        ),
        VoiceCategory.LINK_REQUEST to listOf(
            "click( on)? (the |this |that )?link", "open (the |this |that )?link", "link (par|pe) click",
            "link (bhej|send)\\w*", "sent (you )?(a |the )?link", "download (the |this |an? )?(app|apk|application)",
            "install (the |this |an? )?(app|apk|application)", "apk", "लिंक (पर|पे) क्लिक",
            "लिंक (खोल|ओपन|भेज)\\w*", "(ऐप|एप|एपीके)( \\S+){0,2} (डाउनलोड|इंस्टॉल|इंस्टाल)\\w*", "एपीके",
        ),
    )

    private val PATTERNS: Map<VoiceCategory, List<Regex>> = TERMS.mapValues { (_, ts) -> ts.map(::wordPattern) }

    // OTP requests need both an OTP word and an asking verb in the same clause.
    private val OTP_WORD = wordPattern("(otp|o t p|one time password|verification code|\\d digit code|ओटीपी|ओ टी पी|वन टाइम पासवर्ड|वेरिफिकेशन कोड|(\\d|छह|छः|चार) अंकों का कोड)")
    private val ASK_VERB = wordPattern(
        "(tell|share|read|give|send|say|confirm|forward|provide|bata\\w*|bhej\\w*|bol\\w*|de do|dijiye|dena|बता\\w*|भेज\\w*|बोल\\w*|दे दो|दे दीजिए|दीजिए|दीजिये|देना|शेयर कर\\w*|पढ़\\w*|सुना\\w*)",
    )

    /** "Never share your OTP", "bank never asks for your PIN", "OTP kisi ko mat batana". */
    private val PROTECTIVE = listOf(
        "(never|dont|do not|not to|nobody should) (share|disclose|give|tell|reveal)( \\w+){0,3} (otp|pin|password|cvv|code|details)",
        "(never|will not|wont|does not|doesnt|do not|dont) (ask|call)( \\w+){0,4} (otp|pin|password|cvv|card)",
        "(otp|pin|password|cvv|code)( \\w+){0,3} (kisi ko|kisi ke saath|kisi se|anyone|anybody)( \\w+){0,2} (mat|na|nahi|never)",
        "(otp|pin|password|cvv|code)( \\w+){0,3} (with )?(anyone|anybody)",
        "ओटीपी( \\S+){0,3} किसी को (मत|ना)",
        // Hindi advice: "ओटीपी किसी के साथ भी शेयर न करें", "बैंक कभी भी ओटीपी नहीं मांगता".
        "(ओटीपी|पिन|पासवर्ड|सीवीवी|कोड)( \\S+){0,3} किसी (को|के साथ|से)( भी)?( \\S+){0,2} (मत|ना|न|नहीं)",
        "(कभी|कभी भी)( \\S+){0,3} (ओटीपी|पिन|पासवर्ड|सीवीवी)( \\S+){0,2} (नहीं|न|ना) (मांग|पूछ)\\w*",
        "(ओटीपी|पिन|पासवर्ड|सीवीवी)( \\S+){0,3} (शेयर|साझा) (न|ना|मत) कर\\w*",
    ).map(::wordPattern)

    /** A delivery agent asking for the delivery OTP is normal; such clauses are not OTP requests. */
    private val DELIVERY = wordPattern("(deliver\\w*|order|courier boy|delivery boy|डिलीवरी|ऑर्डर|कूरियर)")

    private val SENTENCES = Regex("[.?!।\\n;]+")
    private val CLAUSES = Regex(",|(?<![\\p{L}\\p{M}\\p{N}])(and|aur|but|lekin|then|phir|और|लेकिन|फिर|किंतु|परंतु)(?![\\p{L}\\p{M}\\p{N}])")

    /** Every tactic found in [text], one entry per category (the first phrase that matched). */
    fun matches(text: String): List<Match> {
        if (text.isBlank()) return emptyList()
        val found = LinkedHashMap<VoiceCategory, String>()
        for (sentence in normalize(text).split(SENTENCES)) {
            for (clause in sentence.split(CLAUSES).map { it.trim() }.filter { it.isNotEmpty() }) {
                clauseMatches(clause).forEach { m ->
                    // Keep "digital arrest" over a plainer arrest word heard earlier: it alone is a red flag.
                    if (m.category == VoiceCategory.ARREST_THREAT && (m.phrase.contains("digital") || m.phrase.contains("डिजिटल"))) {
                        found[m.category] = m.phrase
                    } else {
                        found.putIfAbsent(m.category, m.phrase)
                    }
                }
            }
        }
        return found.map { (c, p) -> Match(c, p) }
    }

    fun categories(text: String): Set<VoiceCategory> = matches(text).map { it.category }.toSet()

    /** The voice events for a transcript chunk or script line heard at [ts]. */
    fun events(ts: Long, text: String): List<SignalEvent.VoiceKeyword> =
        matches(text).map { SignalEvent.VoiceKeyword(ts, it.category, it.phrase) }

    private fun clauseMatches(clause: String): List<Match> = buildList {
        val protective = PROTECTIVE.any { it.containsMatchIn(clause) }
        for ((category, patterns) in PATTERNS) {
            if (protective && category in SILENCED_BY_ADVICE) continue
            patterns.firstNotNullOfOrNull { it.find(clause) }?.let { add(Match(category, it.value)) }
        }
        if (!protective && !DELIVERY.containsMatchIn(clause)) {
            val otp = OTP_WORD.find(clause)
            if (otp != null && ASK_VERB.containsMatchIn(clause)) add(Match(VoiceCategory.OTP_REQUEST, otp.value))
        }
    }

    /** Advice clauses mention PINs and "anyone" but are not a request or a secrecy demand. */
    private val SILENCED_BY_ADVICE = setOf(VoiceCategory.CARD_DETAILS, VoiceCategory.ISOLATION)

    private fun normalize(text: String) = nukta(text).lowercase()
        .replace(Regex("[’'`]"), "")
        .replace(Regex("[\\s_\\-]+"), " ")

    // \b and the JVM's ASCII \w do not work for Devanagari (vowel signs are marks, not letters),
    // and Android's ICU regex rejects the (?U) flag, so spell the word class out explicitly.
    private const val WORD = "[\\p{L}\\p{M}\\p{N}_]"
    private fun wordPattern(term: String) =
        Regex("(?<![\\p{L}\\p{M}\\p{N}])(?:${nukta(term).replace("\\w", WORD)})(?![\\p{L}\\p{M}\\p{N}])")

    /**
     * ड़, फ़, ज़ and the other nukta letters come both as one code point and as letter + nukta,
     * and speech services also differ on writing the nukta at all (गिरफ़्तार / गिरफ्तार). NFC
     * splits them (they are composition exclusions), then the nukta is dropped from terms and
     * transcripts alike.
     */
    private fun nukta(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC).replace("\u093C", "")
}
