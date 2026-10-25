package io.jauge.core.domain

import io.jauge.core.domain.model.AnalysisProgress
import io.jauge.core.domain.model.ImageOrigin
import io.jauge.core.domain.model.OfferSource
import io.jauge.core.domain.model.Outcome
import io.jauge.core.domain.service.PersonalData
import io.jauge.core.domain.service.PersonalData.EMAIL_MARKER
import io.jauge.core.domain.service.PersonalData.NAME_MARKER
import io.jauge.core.domain.service.PersonalData.PHONE_MARKER
import io.jauge.core.domain.usecase.AnalyzeOfferUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PersonalDataTest {

    private fun assertScrubbed(expected: String, text: String) = assertEquals(expected, PersonalData.scrub(text), text)

    private fun assertKept(text: String) = assertEquals(text, PersonalData.scrub(text), text)

    @Test
    fun emailAddressesAreRemoved() {
        assertScrubbed("Envoyez votre CV à $EMAIL_MARKER avant le 15/11.", "Envoyez votre CV à marie.dupont@maison-odier.fr avant le 15/11.")
        assertScrubbed("Candidatures : $EMAIL_MARKER", "Candidatures : recrutement+lyon@groupe-sorel.co.uk")
        assertScrubbed("($EMAIL_MARKER)", "(j.martin_42@gmail.com)")
        assertScrubbed("mailto:$EMAIL_MARKER", "mailto:rh@atelier-sorel.fr")
        assertScrubbed("$EMAIL_MARKER, $EMAIL_MARKER", "a.b@c.io, JOBS@EXAMPLE.COM")
    }

    @Test
    fun frenchPhoneNumbersAreRemoved() {
        listOf(
            "06 12 34 56 78",
            "0612345678",
            "01.23.45.67.89",
            "07-89-12-34-56",
            "04 72 00 00 00",
            "09 12 34 56 78",
            "06 12 34 56 78",
        ).forEach { phone ->
            assertScrubbed("Tél : $PHONE_MARKER.", "Tél : $phone.")
        }
        assertScrubbed("Appelez le $PHONE_MARKER ou le $PHONE_MARKER", "Appelez le 06 12 34 56 78 ou le 01 23 45 67 89")
        assertScrubbed("Tél.$PHONE_MARKER", "Tél.06 12 34 56 78")
        assertScrubbed("N° de téléphone : $PHONE_MARKER", "N° de téléphone : 0612345678")
    }

    @Test
    fun internationalPhoneNumbersAreRemoved() {
        listOf(
            "+33 6 12 34 56 78",
            "+33612345678",
            "+33 (0)1 23 45 67 89",
            "+33 1.23.45.67.89",
            "0033 6 12 34 56 78",
            "+32 470 12 34 56",
            "+41 22 345 67 89",
            "+44 20 7946 0958",
            "+1 415 555 2671",
            "+1 (415) 555-2671",
            "+33 612 345 678",
        ).forEach { phone ->
            assertScrubbed("Joignable au $PHONE_MARKER du lundi au vendredi", "Joignable au $phone du lundi au vendredi")
        }
    }

    @Test
    fun numbersThatAreNotPhonesAreKept() {
        listOf(
            "Salaire : 38 000 € à 42 000 € brut annuel",
            "Rémunération : 38000.00 Euros à 42000.00 Euros sur 12 mois",
            "Salaire : 2 000 - 2 500 € brut mensuel",
            "Prime : +3 000 € par an",
            "Plus de +25 000 000 utilisateurs dans le monde",
            "Chiffre d'affaires : 1 200 000 000 €",
            "+15 % de croissance en 2025",
            "69003 Lyon",
            "Lieu de travail : 75 - Paris 8e Arrondissement (75008)",
            "Code postal 13001 Marseille",
            "SIRET : 552 120 222 00013",
            "SIRET : 55212022200013",
            "SIREN 552 120 222",
            "Offre n° 180ZJKL",
            "Référence : 0612345678",
            "Réf. 2024-0612345678",
            "Ref : JOB-0123456789",
            "Offre publiée le 01/02/2026",
            "Du 01.09.2026 au 30.06.2027",
            "Début : 01 09 2026",
            "Horaires : 09h00 - 18h00, 35 h par semaine",
            "Version 1.06.12.34.56",
            "Numéro de TVA : FR 40 552120222",
            "IBAN masqué, 12 jours de RTT, 25 jours de congés",
            "Expérience : 3 à 5 ans, bac +5",
            "Publiée il y a 2 jours, 47 candidatures",
        ).forEach { assertKept(it) }
    }

    @Test
    fun linksToTheOfferAreKept() {
        listOf(
            "https://www.welcometothejungle.com/fr/companies/maison-odier/jobs/charge-de-marketing-digital_lyon",
            "https://fr.indeed.com/viewjob?jk=0612345678a1b2c3",
            "https://www.hellowork.com/fr-fr/emplois/0612345678.html",
            "https://candidat.francetravail.fr/offres/recherche/detail/180ZJKL",
            "https://www.linkedin.com/jobs/view/0612345678",
            "www.maison-odier.fr/carrieres/06-12-34-56-78",
            "maison-odier.fr/jobs/0123456789",
            "https://medium.com/@maison-odier/nous-recrutons",
            "https://careers.example.com/job?id=33612345678",
        ).forEach { assertKept("Postuler : $it") }
    }

    @Test
    fun namesAfterAContactLabelAreRemoved() {
        assertScrubbed("Contact : $NAME_MARKER", "Contact : Marie Dupont")
        assertScrubbed("Contact: $NAME_MARKER, $PHONE_MARKER", "Contact: Jean-Pierre Martin, 06 12 34 56 78")
        assertScrubbed("Recruteur : $NAME_MARKER", "Recruteur : Thomas LEFEBVRE")
        assertScrubbed("Recruteuse : $NAME_MARKER", "Recruteuse : Élodie Bernard")
        assertScrubbed("Votre contact : $NAME_MARKER ($EMAIL_MARKER)", "Votre contact : Sophie Martin (s.martin@odier.fr)")
        assertScrubbed("Interlocutrice : $NAME_MARKER", "Interlocutrice : Mme Nathalie Roux")
        assertScrubbed("Contact : $NAME_MARKER", "Contact : M. Jean DUPONT")
        assertScrubbed("Personne à contacter : $NAME_MARKER", "Personne à contacter : Hélène de La Fontaine")
        assertScrubbed("Contactez $NAME_MARKER au $PHONE_MARKER", "Contactez Lucas Moreau au 07 12 34 56 78")
        assertScrubbed("Candidature à adresser auprès de $NAME_MARKER.", "Candidature à adresser auprès de Claire Petit.")
        assertScrubbed("Chargée de recrutement : $NAME_MARKER", "Chargée de recrutement : Camille Durand")
        assertScrubbed("Posted by $NAME_MARKER", "Posted by Sarah Cohen")
        assertScrubbed("Contact : $NAME_MARKER Lyon", "Contact : Marie Dupont Lyon")
    }

    @Test
    fun civilitiesAnnounceANameAnywhere() {
        assertScrubbed("Merci d'adresser votre candidature à $NAME_MARKER, DRH.", "Merci d'adresser votre candidature à Mme Durand, DRH.")
        assertScrubbed("Entretien avec $NAME_MARKER et $NAME_MARKER", "Entretien avec M. Bernard et Mme Lucie Garnier")
        assertScrubbed("$NAME_MARKER, directeur", "Monsieur Paul-Henri DE VILLIERS, directeur")
        assertKept("Madame, Monsieur, nous recherchons un(e) comptable.")
        assertKept("Rejoignez Mr Bricolage à Lyon !")
    }

    @Test
    fun signaturesAndRecruiterCardsAreRemoved() {
        assertScrubbed("$NAME_MARKER, Talent Acquisition", "Marie Dupont, Talent Acquisition")
        assertScrubbed("$NAME_MARKER | Chargée de recrutement chez Maison Odier", "Léa Fontaine | Chargée de recrutement chez Maison Odier")
        assertScrubbed("$NAME_MARKER - HR Business Partner", "Thomas Girard - HR Business Partner")
        assertScrubbed(
            "Rencontrez l'équipe de recrutement\n$NAME_MARKER · 2e\nTalent Acquisition Manager chez Maison Odier\nAuteur de l'offre",
            "Rencontrez l'équipe de recrutement\nJulie Lambert · 2e\nTalent Acquisition Manager chez Maison Odier\nAuteur de l'offre",
        )
        assertScrubbed(
            "Meet the hiring team\n$NAME_MARKER\n2nd\nRecruiter at Atelier Sorel\nJob poster",
            "Meet the hiring team\nNicolas Faure\n2nd\nRecruiter at Atelier Sorel\nJob poster",
        )
        assertScrubbed("Votre contact\n$NAME_MARKER\nMaison Odier", "Votre contact\nAntoine Mercier\nMaison Odier")
        assertScrubbed("Cordialement,\n$NAME_MARKER\nResponsable RH", "Cordialement,\nIsabelle Morel\nResponsable RH")
    }

    @Test
    fun whatTheAnalysisNeedsIsKept() {
        listOf(
            "Chargé(e) de marketing digital H/F",
            "Maison Odier",
            "Contact : Maison Odier",
            "Recruteur : Atelier Sorel",
            "Recruteur : Pierre Fabre",
            "Contact : Service RH",
            "Contact : Pôle emploi de Lyon",
            "Entreprise : Yves Rocher\nConseiller(e) de vente",
            "Pierre Fabre\nResponsable recrutement Toulouse",
            "Hugo Boss\nRecrutement en cours",
            "Université Claude Bernard Lyon 1",
            "12 rue Victor Hugo, 69002 Lyon",
            "Lyon 3e, CDI, 38 à 42 k€ brut par an",
            "Compétences : SEO, Google Ads, Figma, Kotlin, Ruby on Rails",
            "Manager : Responsable de boutique",
            "Marie Dupont et Associés recrute un juriste",
            "Développeur Kotlin Multiplatform\nJauge\nParis 11e\nTélétravail partiel",
            "Talent Acquisition Manager H/F",
            "Recrutement : processus en 3 étapes",
            "Contactez-nous via le formulaire",
            "Contact : France Travail",
        ).forEach { assertKept(it) }
    }

    @Test
    fun realOffersAreCleanedWithoutLosingTheOffer() {
        val welcomeToTheJungle = """
            Chargé(e) de marketing digital
            Maison Odier
            CDI · Lyon 3e · Salaire : 38K à 42K €
            Début : 05 janvier 2026 · Télétravail : occasionnel
            Le poste
            Tu rejoins l'équipe marketing (6 personnes) et pilotes nos campagnes SEO et Google Ads.
            Le processus de recrutement
            1. Appel de 30 min avec Marie Dupont, Talent Acquisition
            2. Cas pratique
            Des questions ? marie.dupont@maison-odier.fr
            https://www.welcometothejungle.com/fr/companies/maison-odier/jobs/charge-de-marketing-digital_lyon
        """.trimIndent()
        val cleaned = PersonalData.scrub(welcomeToTheJungle)
        assertFalse("Marie" in cleaned || "dupont" in cleaned)
        assertTrue("1. Appel de 30 min avec $NAME_MARKER, Talent Acquisition" in cleaned)
        assertTrue("Des questions ? $EMAIL_MARKER" in cleaned)
        listOf("Chargé(e) de marketing digital", "Maison Odier", "Lyon 3e", "38K à 42K €", "05 janvier 2026", "SEO et Google Ads",
            "https://www.welcometothejungle.com/fr/companies/maison-odier/jobs/charge-de-marketing-digital_lyon").forEach {
            assertTrue(it in cleaned, it)
        }

        val franceTravail = """
            Offre n° 180ZJKL
            Comptable H/F
            69 - LYON 03
            Publié le 20 octobre 2026
            Contrat à durée indéterminée, 35H/semaine
            Salaire : Annuel de 32000.00 Euros à 36000.00 Euros sur 13 mois
            Entreprise : ATELIER SOREL, 45 salariés
            SIRET 552 120 222 00013
            Contact : M. Jean DUPONT
            Téléphone : 04 72 00 00 00
            Courriel : jean.dupont@atelier-sorel.fr
        """.trimIndent()
        assertEquals(
            """
            Offre n° 180ZJKL
            Comptable H/F
            69 - LYON 03
            Publié le 20 octobre 2026
            Contrat à durée indéterminée, 35H/semaine
            Salaire : Annuel de 32000.00 Euros à 36000.00 Euros sur 13 mois
            Entreprise : ATELIER SOREL, 45 salariés
            SIRET 552 120 222 00013
            Contact : $NAME_MARKER
            Téléphone : $PHONE_MARKER
            Courriel : $EMAIL_MARKER
            """.trimIndent(),
            PersonalData.scrub(franceTravail),
        )

        val indeed = """
            Développeur Kotlin H/F
            Atelier Sorel - Nantes (44)
            45 000 € - 55 000 € par an - CDI
            Postulez sur https://fr.indeed.com/viewjob?jk=a1b2c3d4e5f60718
            Pour toute question : Julien Roche, recruteur, 06 98 76 54 32
        """.trimIndent()
        assertEquals(
            """
            Développeur Kotlin H/F
            Atelier Sorel - Nantes (44)
            45 000 € - 55 000 € par an - CDI
            Postulez sur https://fr.indeed.com/viewjob?jk=a1b2c3d4e5f60718
            Pour toute question : $NAME_MARKER, recruteur, $PHONE_MARKER
            """.trimIndent(),
            PersonalData.scrub(indeed),
        )

        val helloWork = """
            Assistant commercial (H/F)
            Groupe Sorel
            Bordeaux - 33
            CDI - Temps plein
            Salaire : 26 000 € - 29 000 € / an
            Réf. 2026-0612345678
            Envoyez votre candidature à Mme Claire Petit : +33 5 56 00 00 00
        """.trimIndent()
        assertEquals(
            """
            Assistant commercial (H/F)
            Groupe Sorel
            Bordeaux - 33
            CDI - Temps plein
            Salaire : 26 000 € - 29 000 € / an
            Réf. 2026-0612345678
            Envoyez votre candidature à $NAME_MARKER : $PHONE_MARKER
            """.trimIndent(),
            PersonalData.scrub(helloWork),
        )

        val linkedIn = """
            Product Designer
            Jauge · Paris, Île-de-France · il y a 3 jours · 54 candidatures
            Hybride · Temps plein · 50 000 €/an - 60 000 €/an
            Rencontrez l'équipe de recrutement
            Camille Lefèvre · 3e
            Head of People chez Jauge
            Auteur de l'offre
        """.trimIndent()
        val cleanedLinkedIn = PersonalData.scrub(linkedIn)
        assertFalse("Camille" in cleanedLinkedIn)
        assertTrue("$NAME_MARKER · 3e" in cleanedLinkedIn)
        assertTrue("Jauge · Paris, Île-de-France · il y a 3 jours · 54 candidatures" in cleanedLinkedIn)
        assertTrue("50 000 €/an - 60 000 €/an" in cleanedLinkedIn)
    }

    @Test
    fun aTextWithoutPersonalDataIsUnchanged() {
        assertKept(SAMPLE_OFFER_TEXT)
        assertKept("")
        assertKept("   ")
    }

    @Test
    fun aLongTextIsScrubbedQuickly() {
        val text = (SAMPLE_OFFER_TEXT + "\nContact : Marie Dupont, 06 12 34 56 78, marie@odier.fr\n").repeat(200)
        val cleaned = PersonalData.scrub(text)
        assertFalse("06 12" in cleaned || "Marie Dupont" in cleaned || "@" in cleaned)
    }

    @Test
    fun theUseCaseSendsOnlyTheScrubbedText() = runTest {
        val repository = FakeAnalysisRepository()
        val useCase = AnalyzeOfferUseCase(repository) { _, _ ->
            Outcome.Success("$SAMPLE_OFFER_TEXT\nContact : Marie Dupont, 06 12 34 56 78, marie.dupont@odier.fr")
        }
        assertIs<AnalysisProgress.Reading>(useCase(OfferSource.Image(byteArrayOf(1), "image/png", ImageOrigin.SCREENSHOT)).first())
        val sent = assertIs<OfferSource.Text>(repository.receivedSources.single())
        assertEquals("$SAMPLE_OFFER_TEXT\nContact : $NAME_MARKER, $PHONE_MARKER, $EMAIL_MARKER", sent.text)
    }

    @Test
    fun thePastedTextAndTheManualEntryAreScrubbedToo() = runTest {
        val repository = FakeAnalysisRepository()
        val useCase = AnalyzeOfferUseCase(repository) { _, _ -> Outcome.Failure(io.jauge.core.domain.error.JaugeError.Unknown) }
        useCase(OfferSource.Text("$SAMPLE_OFFER_TEXT\nRecruteur : Thomas Girard (thomas@odier.fr)", ImageOrigin.SCREENSHOT)).first()
        useCase(OfferSource.Manual("Comptable, appeler le 06 12 34 56 78", "Lyon")).first()
        val text = assertIs<OfferSource.Text>(repository.receivedSources[0])
        assertEquals("$SAMPLE_OFFER_TEXT\nRecruteur : $NAME_MARKER ($EMAIL_MARKER)", text.text)
        val manual = assertIs<OfferSource.Manual>(repository.receivedSources[1])
        assertEquals("Comptable, appeler le $PHONE_MARKER", manual.jobTitle)
        assertEquals("Lyon", manual.city)
    }
}
