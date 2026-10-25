package io.jauge.core.location

import io.jauge.core.domain.model.LocatedCity
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A position of the phone. It is only compared with [FrenchCities] on the phone and never sent anywhere.
 *
 * @property latitude latitude in degrees.
 * @property longitude longitude in degrees.
 */
data class DevicePosition(val latitude: Double, val longitude: Double)

/**
 * A reference city: every prefecture of France (mainland, Corsica and overseas) and the large cities.
 *
 * @property name name of the city.
 * @property departmentCode department ("69", "2A", "974").
 * @property latitude latitude of the center, in degrees.
 * @property longitude longitude of the center, in degrees.
 */
data class ReferenceCity(val name: String, val departmentCode: String, val latitude: Double, val longitude: Double)

/**
 * Turns a position into the nearest reference city, on the phone, without any network call: the position never leaves
 * the phone and the app keeps only the city and the department.
 */
object FrenchCities {
    /** Positions farther than this from every reference city are outside France: no city. */
    const val MAXIMUM_DISTANCE_KILOMETERS: Double = 80.0

    private const val EARTH_RADIUS_KILOMETERS = 6371.0

    /** Reference cities. */
    val all: List<ReferenceCity> = listOf(
        ReferenceCity("Bourg-en-Bresse", "01", 46.205, 5.226),
        ReferenceCity("Laon", "02", 49.564, 3.620),
        ReferenceCity("Saint-Quentin", "02", 49.848, 3.287),
        ReferenceCity("Moulins", "03", 46.566, 3.333),
        ReferenceCity("Montluçon", "03", 46.340, 2.603),
        ReferenceCity("Vichy", "03", 46.127, 3.425),
        ReferenceCity("Digne-les-Bains", "04", 44.092, 6.236),
        ReferenceCity("Gap", "05", 44.559, 6.079),
        ReferenceCity("Nice", "06", 43.710, 7.262),
        ReferenceCity("Cannes", "06", 43.552, 7.017),
        ReferenceCity("Antibes", "06", 43.580, 7.125),
        ReferenceCity("Privas", "07", 44.735, 4.599),
        ReferenceCity("Charleville-Mézières", "08", 49.773, 4.720),
        ReferenceCity("Foix", "09", 42.965, 1.607),
        ReferenceCity("Troyes", "10", 48.297, 4.074),
        ReferenceCity("Carcassonne", "11", 43.213, 2.349),
        ReferenceCity("Narbonne", "11", 43.184, 3.004),
        ReferenceCity("Rodez", "12", 44.350, 2.575),
        ReferenceCity("Marseille", "13", 43.296, 5.370),
        ReferenceCity("Aix-en-Provence", "13", 43.529, 5.447),
        ReferenceCity("Arles", "13", 43.677, 4.631),
        ReferenceCity("Caen", "14", 49.182, -0.370),
        ReferenceCity("Aurillac", "15", 44.926, 2.440),
        ReferenceCity("Angoulême", "16", 45.648, 0.156),
        ReferenceCity("La Rochelle", "17", 46.160, -1.151),
        ReferenceCity("Bourges", "18", 47.081, 2.399),
        ReferenceCity("Tulle", "19", 45.267, 1.772),
        ReferenceCity("Brive-la-Gaillarde", "19", 45.158, 1.533),
        ReferenceCity("Ajaccio", "2A", 41.919, 8.738),
        ReferenceCity("Bastia", "2B", 42.697, 9.450),
        ReferenceCity("Dijon", "21", 47.322, 5.041),
        ReferenceCity("Saint-Brieuc", "22", 48.514, -2.765),
        ReferenceCity("Guéret", "23", 46.171, 1.871),
        ReferenceCity("Périgueux", "24", 45.184, 0.721),
        ReferenceCity("Besançon", "25", 47.238, 6.024),
        ReferenceCity("Valence", "26", 44.933, 4.892),
        ReferenceCity("Évreux", "27", 49.027, 1.151),
        ReferenceCity("Chartres", "28", 48.443, 1.489),
        ReferenceCity("Quimper", "29", 47.996, -4.102),
        ReferenceCity("Brest", "29", 48.390, -4.486),
        ReferenceCity("Nîmes", "30", 43.837, 4.360),
        ReferenceCity("Toulouse", "31", 43.605, 1.444),
        ReferenceCity("Auch", "32", 43.646, 0.586),
        ReferenceCity("Bordeaux", "33", 44.838, -0.579),
        ReferenceCity("Montpellier", "34", 43.611, 3.877),
        ReferenceCity("Béziers", "34", 43.344, 3.216),
        ReferenceCity("Rennes", "35", 48.117, -1.678),
        ReferenceCity("Saint-Malo", "35", 48.649, -2.026),
        ReferenceCity("Châteauroux", "36", 46.810, 1.691),
        ReferenceCity("Tours", "37", 47.394, 0.685),
        ReferenceCity("Grenoble", "38", 45.188, 5.725),
        ReferenceCity("Lons-le-Saunier", "39", 46.675, 5.556),
        ReferenceCity("Mont-de-Marsan", "40", 43.890, -0.500),
        ReferenceCity("Blois", "41", 47.586, 1.336),
        ReferenceCity("Saint-Étienne", "42", 45.440, 4.387),
        ReferenceCity("Le Puy-en-Velay", "43", 45.043, 3.885),
        ReferenceCity("Nantes", "44", 47.218, -1.554),
        ReferenceCity("Saint-Nazaire", "44", 47.273, -2.214),
        ReferenceCity("Orléans", "45", 47.903, 1.909),
        ReferenceCity("Cahors", "46", 44.448, 1.441),
        ReferenceCity("Agen", "47", 44.203, 0.616),
        ReferenceCity("Mende", "48", 44.518, 3.500),
        ReferenceCity("Angers", "49", 47.478, -0.563),
        ReferenceCity("Saint-Lô", "50", 49.116, -1.090),
        ReferenceCity("Cherbourg-en-Cotentin", "50", 49.640, -1.616),
        ReferenceCity("Châlons-en-Champagne", "51", 48.957, 4.363),
        ReferenceCity("Reims", "51", 49.258, 4.032),
        ReferenceCity("Chaumont", "52", 48.111, 5.139),
        ReferenceCity("Laval", "53", 48.073, -0.770),
        ReferenceCity("Nancy", "54", 48.692, 6.184),
        ReferenceCity("Bar-le-Duc", "55", 48.772, 5.160),
        ReferenceCity("Vannes", "56", 47.658, -2.760),
        ReferenceCity("Lorient", "56", 47.748, -3.370),
        ReferenceCity("Metz", "57", 49.119, 6.176),
        ReferenceCity("Nevers", "58", 46.990, 3.159),
        ReferenceCity("Lille", "59", 50.629, 3.057),
        ReferenceCity("Dunkerque", "59", 51.034, 2.377),
        ReferenceCity("Valenciennes", "59", 50.358, 3.523),
        ReferenceCity("Beauvais", "60", 49.430, 2.081),
        ReferenceCity("Compiègne", "60", 49.418, 2.826),
        ReferenceCity("Alençon", "61", 48.432, 0.091),
        ReferenceCity("Arras", "62", 50.291, 2.777),
        ReferenceCity("Calais", "62", 50.951, 1.858),
        ReferenceCity("Boulogne-sur-Mer", "62", 50.726, 1.614),
        ReferenceCity("Clermont-Ferrand", "63", 45.778, 3.087),
        ReferenceCity("Pau", "64", 43.295, -0.371),
        ReferenceCity("Bayonne", "64", 43.493, -1.475),
        ReferenceCity("Tarbes", "65", 43.233, 0.078),
        ReferenceCity("Perpignan", "66", 42.699, 2.895),
        ReferenceCity("Strasbourg", "67", 48.573, 7.752),
        ReferenceCity("Colmar", "68", 48.079, 7.358),
        ReferenceCity("Mulhouse", "68", 47.750, 7.336),
        ReferenceCity("Lyon", "69", 45.764, 4.836),
        ReferenceCity("Vesoul", "70", 47.622, 6.155),
        ReferenceCity("Mâcon", "71", 46.307, 4.828),
        ReferenceCity("Chalon-sur-Saône", "71", 46.781, 4.854),
        ReferenceCity("Le Mans", "72", 48.006, 0.199),
        ReferenceCity("Chambéry", "73", 45.564, 5.918),
        ReferenceCity("Annecy", "74", 45.899, 6.129),
        ReferenceCity("Paris", "75", 48.857, 2.352),
        ReferenceCity("Rouen", "76", 49.443, 1.100),
        ReferenceCity("Le Havre", "76", 49.494, 0.108),
        ReferenceCity("Melun", "77", 48.540, 2.660),
        ReferenceCity("Meaux", "77", 48.960, 2.879),
        ReferenceCity("Versailles", "78", 48.801, 2.130),
        ReferenceCity("Niort", "79", 46.323, -0.459),
        ReferenceCity("Amiens", "80", 49.894, 2.296),
        ReferenceCity("Albi", "81", 43.929, 2.148),
        ReferenceCity("Montauban", "82", 44.018, 1.355),
        ReferenceCity("Toulon", "83", 43.124, 5.928),
        ReferenceCity("Avignon", "84", 43.949, 4.806),
        ReferenceCity("La Roche-sur-Yon", "85", 46.670, -1.426),
        ReferenceCity("Poitiers", "86", 46.580, 0.340),
        ReferenceCity("Limoges", "87", 45.834, 1.262),
        ReferenceCity("Épinal", "88", 48.173, 6.450),
        ReferenceCity("Auxerre", "89", 47.798, 3.567),
        ReferenceCity("Belfort", "90", 47.640, 6.863),
        ReferenceCity("Évry-Courcouronnes", "91", 48.629, 2.441),
        ReferenceCity("Nanterre", "92", 48.892, 2.207),
        ReferenceCity("Boulogne-Billancourt", "92", 48.835, 2.240),
        ReferenceCity("Bobigny", "93", 48.908, 2.439),
        ReferenceCity("Saint-Denis", "93", 48.936, 2.357),
        ReferenceCity("Créteil", "94", 48.790, 2.455),
        ReferenceCity("Cergy", "95", 49.036, 2.076),
        ReferenceCity("Basse-Terre", "971", 15.998, -61.726),
        ReferenceCity("Pointe-à-Pitre", "971", 16.241, -61.533),
        ReferenceCity("Fort-de-France", "972", 14.616, -61.059),
        ReferenceCity("Cayenne", "973", 4.922, -52.313),
        ReferenceCity("Saint-Denis", "974", -20.882, 55.450),
        ReferenceCity("Saint-Pierre", "974", -21.339, 55.478),
        ReferenceCity("Mamoudzou", "976", -12.781, 45.228),
    )

    /**
     * Nearest reference city.
     *
     * @param position position of the phone.
     * @return the city, or null when the position is farther than [MAXIMUM_DISTANCE_KILOMETERS] from every city.
     */
    fun nearest(position: DevicePosition): LocatedCity? {
        if (!position.latitude.isFinite() || !position.longitude.isFinite()) return null
        val closest = all.minBy { distanceKilometers(position, it) }
        return if (distanceKilometers(position, closest) <= MAXIMUM_DISTANCE_KILOMETERS) {
            LocatedCity(closest.name, closest.departmentCode)
        } else {
            null
        }
    }

    /**
     * Finds a city by name, ignoring case, accents, dashes and spaces ("saint etienne" finds "Saint-Étienne").
     *
     * @param name typed name.
     * @return the city, or null when it is not a reference city.
     */
    fun byName(name: String): LocatedCity? {
        val key = normalize(name)
        if (key.isEmpty()) return null
        return all.firstOrNull { normalize(it.name) == key }?.let { LocatedCity(it.name, it.departmentCode) }
    }

    /**
     * Great circle distance.
     *
     * @param position position of the phone.
     * @param city reference city.
     */
    fun distanceKilometers(position: DevicePosition, city: ReferenceCity): Double {
        val latitudeDelta = (city.latitude - position.latitude).toRadians()
        val longitudeDelta = (city.longitude - position.longitude).toRadians()
        val haversine = sin(latitudeDelta / 2).pow(2) +
            cos(position.latitude.toRadians()) * cos(city.latitude.toRadians()) * sin(longitudeDelta / 2).pow(2)
        return 2 * EARTH_RADIUS_KILOMETERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }

    private fun Double.toRadians(): Double = this * kotlin.math.PI / 180.0

    private fun normalize(text: String): String = text.trim().lowercase()
        .map { character -> ACCENTS[character] ?: character }
        .filter { it.isLetterOrDigit() }
        .joinToString("")

    private val ACCENTS: Map<Char, Char> = mapOf(
        'à' to 'a', 'â' to 'a', 'ä' to 'a', 'ç' to 'c', 'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
        'î' to 'i', 'ï' to 'i', 'ô' to 'o', 'ö' to 'o', 'ù' to 'u', 'û' to 'u', 'ü' to 'u', 'ÿ' to 'y',
    )
}
