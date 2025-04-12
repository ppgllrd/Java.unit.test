package test.unit;

import java.util.Map;
import java.util.HashMap;

/**
 * Provides internationalization (I18n) support by storing and retrieving
 * localized message strings based on a key and a {@link Language}.
 * Used by {@link Config#msg(String, Object...)} to format messages for test output.
 *
 * @author Pepe Gallardo & Gemini
 */
public final class I18n {

    private I18n() {} // Prevent instantiation

    private static final Map<Language, Map<String, String>> messages = new HashMap<>();

    static {
        // --- English Messages ---
        Map<String, String> en = new HashMap<>();
        en.put("but.expected", "But %s was expected"); // Used for wrong type/message failures
        // --- Timeout Key ---
        // %1$s = Description of the overall expectation (e.g., "the exception IOException", "result to be 5")
        // %2$d = Timeout duration in seconds
        en.put("timeout", "%s\n   Timeout: test took more than %d seconds to complete");
        // --- Other Keys ---
        en.put("unexpected.exception", "%s\n   Raised unexpected exception %s with message %s"); // %1$=original expectation, %2$=thrown type, %3$=thrown message
        en.put("connector.or", " or ");
        en.put("failed", "TEST FAILED!");
        en.put("passed", "TEST PASSED SUCCESSFULLY!");
        en.put("expected", "%s was expected"); // %1$=expected value
        en.put("expected.result", "Expected result was: %s"); // %1$=expected value
        en.put("obtained.result", "Obtained result was: %s"); // %1$=actual value
        en.put("no.exception.basic", "Expected exception but none was thrown. %s was expected"); // %1$=expected exception description
        en.put("wrong.exception.type.basic", "Test threw exception %s"); // %1$=actual thrown type
        en.put("wrong.exception.message.basic", "Test threw expected exception type %s but message was %s"); // %1$=expected type, %2$=actual message
        en.put("wrong.exception.and.message.basic", "Test threw exception %s with message %s"); // %1$=actual type, %2$=actual message
        en.put("exception.description", "The exception %s"); // %1$=type name(s)
        en.put("exception.with.message.description", "The exception %s with message %s"); // %1$=type name(s), %2$=exact message
        en.put("exception.with.predicate.description", "The exception %s with message satisfying: %s"); // %1$=type name(s), %2$=predicate help
        en.put("exception.oneof.description", "One of exceptions %s"); // %1$=type name list
        en.put("exception.oneof.with.message.description", "One of exceptions %s with message %s"); // %1$=type name list, %2$=exact message
        en.put("exception.oneof.with.predicate.description", "One of exceptions %s with message satisfying: %s"); // %1$=type name list, %2$=predicate help
        en.put("exception.except.description", "Any exception except %s"); // %1$=excluded type name
        en.put("exception.except.with.message.description", "Any exception except %s, with message %s"); // %1$=excluded type name, %2$=exact message
        en.put("exception.except.with.predicate.description", "Any exception except %s, with message satisfying: %s");  // %1$=excluded type name, %2$=predicate help
        en.put("detail.expected_exact_message", "Expected message was %s"); // %1$=exact message detail
        en.put("detail.expected_predicate", "Message should satisfy: %s"); // %1$=predicate help detail
        en.put("property.failure.base", "Does not verify expected property"); // Base message for property failures
        en.put("property.failure.suffix", ": %s"); // Suffix added when property description is available, %1$=property description
        en.put("property.must.be.true", "property should be true"); // Help text for Assert
        en.put("property.must.be.false", "property should be false"); // Help text for Refute
        en.put("property.was.true", "property was true"); // Result formatting for Assert/Refute failures
        en.put("property.was.false", "property was false"); // Result formatting for Assert/Refute failures
        en.put("suite.for", "Tests for %s"); // %1$=suite name
        en.put("results.passed", "Passed");
        en.put("results.failed", "Failed");
        en.put("results.total", "Total");
        en.put("results.detail", "Detail");
        en.put("summary.tittle", "Overall Summary");
        en.put("summary.suites.run", "Suites run: %d"); // %1$=number of suites
        en.put("summary.total.tests", "Total tests: %d"); // %1$=total tests
        en.put("summary.success.rate", "Success rate: %.2f%%"); // %1$=success rate percentage
        // Add English messages to the map
        messages.put(Language.ENGLISH, Map.copyOf(en)); // Use immutable copy

        // --- Spanish Messages ---
        Map<String, String> es = new HashMap<>();
        es.put("but.expected", "Pero se esperaba %s");
        es.put("timeout", "%s\n   Tiempo excedido: la prueba tardó más de %d segundos en completarse");
        es.put("unexpected.exception", "%s\n   Se lanzó la excepción inesperada %s con mensaje %s");
        es.put("connector.or", " o ");
        es.put("failed", "¡PRUEBA FALLIDA!");
        es.put("passed", "¡PRUEBA SUPERADA CON ÉXITO!");
        es.put("expected", "%s se esperaba");
        es.put("expected.result", "El resultado esperado era: %s");
        es.put("obtained.result", "El resultado obtenido fue: %s");
        es.put("no.exception.basic", "Se esperaba una excepción pero no se lanzó ninguna. %s se esperaba");
        es.put("wrong.exception.type.basic", "La prueba lanzó la excepción %s");
        es.put("wrong.exception.message.basic", "La prueba lanzó el tipo de excepción esperado %s pero con mensaje %s");
        es.put("wrong.exception.and.message.basic", "La prueba lanzó la excepción %s con mensaje %s");
        es.put("exception.description", "La excepción %s");
        es.put("exception.with.message.description", "La excepción %s con mensaje %s");
        es.put("exception.with.predicate.description", "La excepción %s con mensaje satisfaciendo: %s");
        es.put("exception.oneof.description", "Una de las excepciones %s");
        es.put("exception.oneof.with.message.description", "Una de las excepciones %s con mensaje %s");
        es.put("exception.oneof.with.predicate.description", "Una de las excepciones %s con mensaje satisfaciendo: %s");
        es.put("exception.except.description", "Cualquier excepción excepto %s");
        es.put("exception.except.with.message.description", "Cualquier excepción excepto %s, con mensaje %s");
        es.put("exception.except.with.predicate.description", "Cualquier excepción excepto %s, con mensaje satisfaciendo: %s");
        es.put("detail.expected_exact_message", "Se esperaba el mensaje %s");
        es.put("detail.expected_predicate", "Se esperaba un mensaje satisfaciendo: %s");
        es.put("property.failure.base", "No verifica la propiedad esperada");
        es.put("property.failure.suffix", ": %s");
        es.put("property.must.be.true", "la propiedad debe ser verdadera");
        es.put("property.must.be.false", "la propiedad debe ser falsa");
        es.put("property.was.true", "la propiedad fue verdadera");
        es.put("property.was.false", "la propiedad fue falsa");
        es.put("suite.for", "Pruebas para %s");
        es.put("results.passed", "Superadas");
        es.put("results.failed", "Fallidas");
        es.put("results.total", "Total");
        es.put("results.detail", "Detalle");
        es.put("summary.tittle", "Resumen General");
        es.put("summary.suites.run", "Suites ejecutadas: %d");
        es.put("summary.total.tests", "Total de pruebas: %d");
        es.put("summary.success.rate", "Tasa de éxito: %.2f%%");
        // Add Spanish messages to the map
        messages.put(Language.SPANISH, Map.copyOf(es)); // Use immutable copy

        // --- French Messages ---
        Map<String, String> fr = new HashMap<>();
        fr.put("but.expected", "Mais %s était attendu");
        fr.put("timeout", "%s\n   Délai dépassé: le test a mis plus de %d secondes à se terminer");
        fr.put("unexpected.exception", "%s\n   L''exception inattendue %s a été levée avec le message %s");
        fr.put("connector.or", " ou ");
        fr.put("failed", "ÉCHEC DU TEST!");
        fr.put("passed", "TEST RÉUSSI AVEC SUCCÈS!");
        fr.put("expected", "%s était attendu");
        fr.put("expected.result", "Le résultat attendu était: %s");
        fr.put("obtained.result", "Le résultat obtenu était: %s");
        fr.put("no.exception.basic", "Exception attendue mais aucune n''a été lancée. %s était attendu");
        fr.put("wrong.exception.type.basic", "Le test a lancé l''exception %s");
        fr.put("wrong.exception.message.basic", "Le test a lancé le type d''exception attendu %s mais le message était %s");
        fr.put("wrong.exception.and.message.basic", "Le test a lancé l''exception %s avec le message %s");
        fr.put("exception.description", "L''exception %s");
        fr.put("exception.with.message.description", "L''exception %s avec le message %s");
        fr.put("exception.with.predicate.description", "L''exception %s avec message satisfaisant : %s");
        fr.put("exception.oneof.description", "Une des exceptions %s");
        fr.put("exception.oneof.with.message.description", "Une des exceptions %s avec le message %s");
        fr.put("exception.oneof.with.predicate.description", "Une des exceptions %s avec message satisfaisant : %s");
        fr.put("exception.except.description", "Toute exception sauf %s");
        fr.put("exception.except.with.message.description", "Toute exception sauf %s, avec le message %s");
        fr.put("exception.except.with.predicate.description", "Toute exception sauf %s, avec message satisfaisant : %s");
        fr.put("detail.expected_exact_message", "Message attendu : %s");
        fr.put("detail.expected_predicate", "Attendu message satisfaisant : %s");
        fr.put("property.failure.base", "Ne vérifie pas la propriété attendue");
        fr.put("property.failure.suffix", " : %s");
        fr.put("property.must.be.true", "la propriété doit être vraie");
        fr.put("property.must.be.false", "la propriété doit être fausse");
        fr.put("property.was.true", "la propriété était vraie");
        fr.put("property.was.false", "la propriété était fausse");
        fr.put("suite.for", "Tests pour %s");
        fr.put("results.passed", "Réussis");
        fr.put("results.failed", "Échoués");
        fr.put("results.total", "Total");
        fr.put("results.detail", "Détail");
        fr.put("summary.tittle", "Résumé Général");
        fr.put("summary.suites.run", "Suites exécutées: %d");
        fr.put("summary.total.tests", "Total des tests: %d");
        fr.put("summary.success.rate", "Taux de réussite: %.2f%%");
        // Add French messages to the map
        messages.put(Language.FRENCH, Map.copyOf(fr)); // Use immutable copy
    }

    /**
     * Retrieves the message pattern associated with the given key for the specified language.
     * If the key or language is not found, it falls back to English. If the key is still
     * not found in English, the key itself is returned.
     *
     * @param key The key identifying the desired message pattern.
     * @param language The target {@link Language}.
     * @return The localized message pattern string, or the key if not found.
     */
    public static String getMessage(String key, Language language) {
        return messages.getOrDefault(language, messages.get(Language.ENGLISH))
                       .getOrDefault(key, key);
    }
}
