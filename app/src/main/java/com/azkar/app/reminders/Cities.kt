package com.azkar.app.reminders

/**
 * Cities offered when the user sets the prayer-time location by hand (no location permission needed, all offline).
 * Coordinates are city centres; prayer times barely change within a city.
 */
object Cities {
    data class City(val id: String, val en: String, val ar: String, val lat: Double, val lng: Double) {
        fun name(arabic: Boolean) = if (arabic) ar else en
    }

    val ALL = listOf(
        City("makkah", "Makkah", "مكة المكرمة", 21.4225, 39.8262),
        City("madinah", "Madinah", "المدينة المنورة", 24.4672, 39.6111),
        City("riyadh", "Riyadh", "الرياض", 24.7136, 46.6753),
        City("jeddah", "Jeddah", "جدة", 21.4858, 39.1925),
        City("dammam", "Dammam", "الدمام", 26.4207, 50.0888),
        City("dubai", "Dubai", "دبي", 25.2048, 55.2708),
        City("abu_dhabi", "Abu Dhabi", "أبوظبي", 24.4539, 54.3773),
        City("doha", "Doha", "الدوحة", 25.2854, 51.5310),
        City("kuwait", "Kuwait City", "مدينة الكويت", 29.3759, 47.9774),
        City("manama", "Manama", "المنامة", 26.2285, 50.5860),
        City("muscat", "Muscat", "مسقط", 23.5880, 58.3829),
        City("sanaa", "Sanaa", "صنعاء", 15.3694, 44.1910),
        City("aden", "Aden", "عدن", 12.7855, 45.0187),
        City("amman", "Amman", "عمّان", 31.9454, 35.9284),
        City("jerusalem", "Jerusalem", "القدس", 31.7683, 35.2137),
        City("gaza", "Gaza", "غزة", 31.5017, 34.4668),
        City("beirut", "Beirut", "بيروت", 33.8938, 35.5018),
        City("damascus", "Damascus", "دمشق", 33.5138, 36.2765),
        City("aleppo", "Aleppo", "حلب", 36.2021, 37.1343),
        City("baghdad", "Baghdad", "بغداد", 33.3152, 44.3661),
        City("basra", "Basra", "البصرة", 30.5085, 47.7804),
        City("erbil", "Erbil", "أربيل", 36.1911, 44.0092),
        City("cairo", "Cairo", "القاهرة", 30.0444, 31.2357),
        City("alexandria", "Alexandria", "الإسكندرية", 31.2001, 29.9187),
        City("khartoum", "Khartoum", "الخرطوم", 15.5007, 32.5599),
        City("tripoli", "Tripoli (Libya)", "طرابلس (ليبيا)", 32.8872, 13.1913),
        City("tunis", "Tunis", "تونس", 36.8065, 10.1815),
        City("algiers", "Algiers", "الجزائر", 36.7538, 3.0588),
        City("casablanca", "Casablanca", "الدار البيضاء", 33.5731, -7.5898),
        City("rabat", "Rabat", "الرباط", 34.0209, -6.8416),
        City("marrakesh", "Marrakesh", "مراكش", 31.6295, -7.9811),
        City("nouakchott", "Nouakchott", "نواكشوط", 18.0735, -15.9582),
        City("mogadishu", "Mogadishu", "مقديشو", 2.0469, 45.3182),
        City("istanbul", "Istanbul", "إسطنبول", 41.0082, 28.9784),
        City("ankara", "Ankara", "أنقرة", 39.9334, 32.8597),
        City("tehran", "Tehran", "طهران", 35.6892, 51.3890),
        City("karachi", "Karachi", "كراتشي", 24.8607, 67.0011),
        City("lahore", "Lahore", "لاهور", 31.5204, 74.3587),
        City("islamabad", "Islamabad", "إسلام آباد", 33.6844, 73.0479),
        City("delhi", "Delhi", "دلهي", 28.7041, 77.1025),
        City("dhaka", "Dhaka", "دكا", 23.8103, 90.4125),
        City("jakarta", "Jakarta", "جاكرتا", -6.2088, 106.8456),
        City("kuala_lumpur", "Kuala Lumpur", "كوالالمبور", 3.1390, 101.6869),
        City("singapore", "Singapore", "سنغافورة", 1.3521, 103.8198),
        City("lagos", "Lagos", "لاغوس", 6.5244, 3.3792),
        City("dakar", "Dakar", "داكار", 14.7167, -17.4677),
        City("london", "London", "لندن", 51.5074, -0.1278),
        City("paris", "Paris", "باريس", 48.8566, 2.3522),
        City("brussels", "Brussels", "بروكسل", 50.8503, 4.3517),
        City("amsterdam", "Amsterdam", "أمستردام", 52.3676, 4.9041),
        City("berlin", "Berlin", "برلين", 52.5200, 13.4050),
        City("stockholm", "Stockholm", "ستوكهولم", 59.3293, 18.0686),
        City("new_york", "New York", "نيويورك", 40.7128, -74.0060),
        City("chicago", "Chicago", "شيكاغو", 41.8781, -87.6298),
        City("toronto", "Toronto", "تورنتو", 43.6532, -79.3832),
        City("los_angeles", "Los Angeles", "لوس أنجلوس", 34.0522, -118.2437),
        City("sydney", "Sydney", "سيدني", -33.8688, 151.2093),
    )

    fun byId(id: String?): City? = ALL.firstOrNull { it.id == id }

    /** Valid manual coordinates: a real latitude / longitude pair. */
    fun valid(lat: Double, lng: Double) = lat in -90.0..90.0 && lng in -180.0..180.0
}
