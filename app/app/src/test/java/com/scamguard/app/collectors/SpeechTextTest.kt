package com.scamguard.app.collectors

import com.scamguard.app.bus.VoiceCategory
import com.scamguard.app.bus.VoiceCategory.ACCUSATION
import com.scamguard.app.bus.VoiceCategory.ARREST_THREAT
import com.scamguard.app.bus.VoiceCategory.AUTHORITY_CLAIM
import com.scamguard.app.bus.VoiceCategory.CARD_DETAILS
import com.scamguard.app.bus.VoiceCategory.ISOLATION
import com.scamguard.app.bus.VoiceCategory.LINK_REQUEST
import com.scamguard.app.bus.VoiceCategory.OTP_REQUEST
import com.scamguard.app.bus.VoiceCategory.PAYMENT_PRESSURE
import com.scamguard.app.bus.VoiceCategory.REMOTE_APP_REQUEST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {
    private fun cats(text: String) = SpeechText.categories(text)
    private fun assertCats(text: String, vararg expected: VoiceCategory) = assertEquals(text, expected.toSet(), cats(text))

    @Test fun `english digital arrest lines`() {
        assertCats("This is Inspector Rao from the CBI cyber cell. A parcel in your name was found with illegal items.", AUTHORITY_CLAIM, ACCUSATION)
        assertCats("You are under digital arrest. Do not disconnect this call and do not tell anyone, not even your family.", ARREST_THREAT, ISOLATION)
        assertCats("To verify your accounts you must transfer your money to a safe RBI account right now.", PAYMENT_PRESSURE)
        assertCats("An FIR has been registered against you for money laundering.", ACCUSATION)
        assertCats("Keep your camera on and stay on the video call.", ISOLATION)
    }

    @Test fun `phrasings heard on the phone`() {
        assertCats("I am digital arresting you", ARREST_THREAT)
        assertCats("send me 10000", PAYMENT_PRESSURE)
        assertCats("Pay Rs 5,000 now to close the case", PAYMENT_PRESSURE)
        assertCats("Pachas hazaar bhejo abhi", PAYMENT_PRESSURE)
        assertCats("I will call you at 10 tomorrow")
    }

    @Test fun `hinglish lines`() {
        assertCats("Main CBI cyber cell se bol raha hoon. Aapke naam par ek parcel mein drugs mile hain.", AUTHORITY_CLAIM, ACCUSATION)
        assertCats("Aap abhi digital arrest mein hain. Phone mat kaatna aur kisi ko mat batana.", ARREST_THREAT, ISOLATION)
        assertCats("Verification ke liye paise abhi safe account mein transfer karo.", PAYMENT_PRESSURE)
        assertCats("Aapka OTP bataiye, verify karna hai.", OTP_REQUEST)
        assertCats("Nahi toh aapko giraftar kar lenge.", ARREST_THREAT)
    }

    @Test fun `devanagari lines`() {
        assertCats("मैं सीबीआई से बोल रहा हूँ।", AUTHORITY_CLAIM)
        assertCats("आपको गिरफ्तार किया जाएगा। फोन मत काटना।", ARREST_THREAT, ISOLATION)
    }

    @Test fun `credential and remote access requests`() {
        assertCats("Please tell me the OTP you just received.", OTP_REQUEST)
        assertCats("Read out the 6 digit code from the SMS.", OTP_REQUEST)
        assertCats("Give me your card number, expiry date and CVV.", CARD_DETAILS)
        assertCats("Install AnyDesk so I can verify your account.", REMOTE_APP_REQUEST)
        assertCats("Download this app from the link I sent.", LINK_REQUEST)
        assertCats("Click on the link I sent you.", LINK_REQUEST)
    }

    @Test fun `a request followed by secrecy in one sentence is both`() {
        assertCats("Tell me the OTP and don't tell anyone about this call.", OTP_REQUEST, ISOLATION)
        assertCats("Aapka OTP mujhe batao aur kisi ko mat batana.", OTP_REQUEST, ISOLATION)
    }

    @Test fun `protective advice is not a request`() {
        assertCats("Please never share your OTP or PIN with anyone.")
        assertCats("The bank will never ask for your OTP or card details.")
        assertCats("OTP kisi ko mat batana.")
        assertCats("Do not share your CVV with anybody.")
    }

    @Test fun `delivery OTP is not a request`() {
        assertCats("I am outside your gate with your order. Please tell me the delivery OTP.")
        assertCats("Delivery ke liye OTP bata dijiye.")
    }

    @Test fun `ordinary talk matches nothing`() {
        listOf(
            "Hi, how are you? I will reach home by seven.",
            "Your credit card bill is due on the fifth, you can pay it in the app.",
            "Can you send me the photos from the trip?",
            "",
        ).forEach { assertTrue(it, cats(it).isEmpty()) }
    }

    @Test fun `hindi digital arrest script`() {
        assertCats("मैं सीबीआई साइबर सेल से इंस्पेक्टर राव बोल रहा हूं।", AUTHORITY_CLAIM)
        assertCats("आपके नाम पर एक पार्सल में ड्रग्स मिले हैं।", ACCUSATION)
        assertCats("आपके खिलाफ मनी लॉन्ड्रिंग का केस दर्ज हुआ है।", ACCUSATION)
        assertCats("आप डिजिटल अरेस्ट में हैं।", ARREST_THREAT)
        assertCats("पुलिस आपके घर आ जाएगी।", AUTHORITY_CLAIM, ARREST_THREAT)
        assertCats("फोन मत काटना और किसी को भी मत बताना।", ISOLATION)
        assertCats("घरवालों को कुछ नहीं बताना।", ISOLATION)
        assertCats("वेरिफिकेशन के लिए अपने सारे पैसे सेफ अकाउंट में ट्रांसफर कीजिए।", PAYMENT_PRESSURE)
    }

    @Test fun `hindi credential and app requests`() {
        assertCats("अपना ओटीपी बताइए।", OTP_REQUEST)
        assertCats("अपने डेबिट कार्ड का नंबर और सीवीवी बताइए।", CARD_DETAILS)
        assertCats("एनीडेस्क ऐप डाउनलोड कीजिए।", REMOTE_APP_REQUEST, LINK_REQUEST)
    }

    @Test fun `hindi nukta spellings match either way`() {
        // फ़ as one code point (U+095E), and ड़ as letter + nukta: speech services emit both.
        assertCats("आपको गिरफ़्तार किया जाएगा।", ARREST_THREAT)
        assertCats("आपका बेटा पकड़ा गया है।", ACCUSATION)
    }

    @Test fun `hindi advice, delivery and genuine calls are not flagged`() {
        assertCats("बैंक कभी भी आपसे ओटीपी नहीं मांगता।")
        assertCats("ओटीपी किसी के साथ भी शेयर न करें।")
        assertCats("अपना ओटीपी किसी को मत बताना।")
        assertCats("मैं डिलीवरी बॉय हूं, ऑर्डर के लिए ओटीपी बता दीजिए।")
        assertCats("आपके क्रेडिट कार्ड का बिल कल देय है, आप ऐप में भुगतान कर सकते हैं।")
        assertCats("यह ट्रैफिक पुलिस है, आपकी गाड़ी पर चालान बकाया है।", AUTHORITY_CLAIM)
    }

    @Test fun `matches keep the phrase that matched`() {
        val m = SpeechText.matches("You are under digital arrest.")
        assertEquals("digital arrest", m.single().phrase)
    }
}
