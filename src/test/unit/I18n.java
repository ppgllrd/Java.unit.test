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
        en.put("but.expected", "but %s was expected"); // Used for wrong type/message failures
        // --- Timeout Key ---
        // %1$s = Description of the overall expectation (e.g., "the exception IOException", "result to be 5")
        // %2$d = Timeout duration in seconds
        en.put("timeout", "%s\n   timeout: test took more than %d seconds to complete");
        // --- Other Keys ---
        en.put("unexpected.exception", "%s\n   raised unexpected exception %s with message %s"); // %1$=original expectation, %2$=thrown type, %3$=thrown message
        en.put("connector.or", " or ");
        en.put("failed", "TEST FAILED!");
        en.put("passed", "TEST PASSED SUCCESSFULLY!");
        en.put("expected", "%s was expected"); // %1$=expected value
        en.put("expected.result", "expected result was %s"); // %1$=expected value
        en.put("obtained.result", "obtained result was %s"); // %1$=actual value
        en.put("no.exception.basic", "expected exception but none was thrown. %s was expected"); // %1$=expected exception description
        en.put("wrong.exception.type.basic", "test threw the exception %s"); // %1$=actual thrown type
        en.put("wrong.exception.message.basic", "test threw expected exception type %s but message was %s"); // %1$=expected type, %2$=actual message
        en.put("wrong.exception.and.message.basic", "test threw exception %s with message %s"); // %1$=actual type, %2$=actual message
        en.put("exception.description", "the exception %s"); // %1$=type name(s)
        en.put("exception.with.message.description", "the exception %s with message %s"); // %1$=type name(s), %2$=exact message
        en.put("exception.with.predicate.description", "the exception %s with message satisfying: %s"); // %1$=type name(s), %2$=predicate help
        en.put("exception.oneof.description", "one of exceptions %s"); // %1$=type name list
        en.put("exception.oneof.with.message.description", "one of exceptions %s with message %s"); // %1$=type name list, %2$=exact message
        en.put("exception.oneof.with.predicate.description", "one of exceptions %s with message satisfying: %s"); // %1$=type name list, %2$=predicate help
        en.put("exception.except.description", "any exception except %s"); // %1$=excluded type name
        en.put("exception.except.with.message.description", "any exception except %s, with message %s"); // %1$=excluded type name, %2$=exact message
        en.put("exception.except.with.predicate.description", "any exception except %s, with message satisfying: %s");  // %1$=excluded type name, %2$=predicate help
        en.put("detail.expected_exact_message", "expected message was %s"); // %1$=exact message detail
        en.put("detail.expected_predicate", "message should satisfy: %s"); // %1$=predicate help detail
        en.put("property.failure.base", "does not verify expected property"); // Base message for property failures
        en.put("property.failure.suffix", ": %s"); // Suffix added when property description is available, %1$=property description
        en.put("property.must.be.true", "should be true"); // Help text for Assert
        en.put("property.must.be.false", "should be false"); // Help text for Refute
        en.put("property.was.true", "property was true"); // Result formatting for Assert/Refute failures
        en.put("property.was.false", "property was false"); // Result formatting for Assert/Refute failures
        en.put("suite.for", "Tests for %s"); // %1$=suite name
        en.put("results.passed", "Passed");
        en.put("results.failed", "Failed");
        en.put("results.total", "Total");
        en.put("results.detail", "Detail");
        en.put("summary.title", "Overall summary");
        en.put("summary.suites.run", "Suites run: %d"); // %1$=number of suites
        en.put("summary.total.tests", "Total tests: %d"); // %1$=total tests
        en.put("summary.success.rate", "Success rate: %.2f%%"); // %1$=success rate percentage
        // Add English messages to the map
        messages.put(Language.ENGLISH, Map.copyOf(en)); // Use immutable copy

        // --- Spanish Messages ---
        Map<String, String> es = new HashMap<>();
        es.put("but.expected", "pero se esperaba %s"); // Used for wrong type/message failures
        // --- Timeout Key ---
        // %1$s = Description of the overall expectation (e.g., "la excepción IOException", "resultado sea 5")
        // %2$d = Timeout duration in seconds
        es.put("timeout", "%s\n   tiempo excedido: la prueba tardó más de %d segundos en completarse");
        // --- Other Keys ---
        es.put("unexpected.exception", "%s\n   se lanzó la excepción inesperada %s con mensaje %s"); // %1$=original expectation, %2$=thrown type, %3$=thrown message
        es.put("connector.or", " o ");
        es.put("failed", "¡PRUEBA FALLIDA!");
        es.put("passed", "¡PRUEBA SUPERADA CON ÉXITO!");
        es.put("expected", "%s se esperaba"); // %1$=expected value
        es.put("expected.result", "el resultado esperado era %s"); // %1$=expected value
        es.put("obtained.result", "el resultado obtenido fue %s"); // %1$=actual value
        es.put("no.exception.basic", "se esperaba una excepción pero no se lanzó ninguna. %s se esperaba"); // %1$=expected exception description
        es.put("wrong.exception.type.basic", "la prueba lanzó la excepción %s"); // %1$=actual thrown type
        es.put("wrong.exception.message.basic", "la prueba lanzó el tipo de excepción esperado %s pero el mensaje fue %s"); // %1$=expected type, %2$=actual message
        es.put("wrong.exception.and.message.basic", "la prueba lanzó la excepción %s con mensaje %s"); // %1$=actual type, %2$=actual message
        es.put("exception.description", "la excepción %s"); // %1$=type name(s)
        es.put("exception.with.message.description", "la excepción %s con mensaje %s"); // %1$=type name(s), %2$=exact message
        es.put("exception.with.predicate.description", "la excepción %s con mensaje satisfaciendo: %s"); // %1$=type name(s), %2$=predicate help
        es.put("exception.oneof.description", "una de las excepciones %s"); // %1$=type name list
        es.put("exception.oneof.with.message.description", "una de las excepciones %s con mensaje %s"); // %1$=type name list, %2$=exact message
        es.put("exception.oneof.with.predicate.description", "una de las excepciones %s con mensaje satisfaciendo: %s"); // %1$=type name list, %2$=predicate help
        es.put("exception.except.description", "cualquier excepción excepto %s"); // %1$=excluded type name
        es.put("exception.except.with.message.description", "cualquier excepción excepto %s, con mensaje %s"); // %1$=excluded type name, %2$=exact message
        es.put("exception.except.with.predicate.description", "cualquier excepción excepto %s, con mensaje satisfaciendo: %s");  // %1$=excluded type name, %2$=predicate help
        es.put("detail.expected_exact_message", "se esperaba el mensaje %s"); // %1$=exact message detail
        es.put("detail.expected_predicate", "el mensaje debía satisfacer: %s"); // %1$=predicate help detail
        es.put("property.failure.base", "no verifica la propiedad esperada"); // Base message for property failures
        es.put("property.failure.suffix", ": %s"); // Suffix added when property description is available, %1$=property description
        es.put("property.must.be.true", "debe ser verdadera"); // Help text for Assert
        es.put("property.must.be.false", "debe ser falsa"); // Help text for Refute
        es.put("property.was.true", "la propiedad fue verdadera"); // Result formatting for Assert/Refute failures
        es.put("property.was.false", "la propiedad fue falsa"); // Result formatting for Assert/Refute failures
        es.put("suite.for", "Pruebas para %s"); // %1$=suite name
        es.put("results.passed", "Superadas");
        es.put("results.failed", "Fallidas");
        es.put("results.total", "Total");
        es.put("results.detail", "Detalle");
        es.put("summary.title", "Resumen general");
        es.put("summary.suites.run", "Suites ejecutadas: %d"); // %1$=number of suites
        es.put("summary.total.tests", "Total de pruebas: %d"); // %1$=total tests
        es.put("summary.success.rate", "Tasa de éxito: %.2f%%"); // %1$=success rate percentage
        // Add Spanish messages to the map
        messages.put(Language.SPANISH, Map.copyOf(es)); // Use immutable copy


        // --- French Messages ---
        // --- French Messages (Updated with original comments) ---
        Map<String, String> fr = new HashMap<>();
        fr.put("but.expected", "mais %s était attendu"); // Used for wrong type/message failures
        // --- Timeout Key ---
        // %1$s = Description de l'attente globale (par ex., "l'exception IOException", "résultat soit 5")
        // %2$d = Durée du timeout en secondes
        fr.put("timeout", "%s\n   délai dépassé : le test a mis plus de %d secondes à se terminer");
        // --- Other Keys ---
        fr.put("unexpected.exception", "%s\n   a levé l'exception inattendue %s avec le message %s"); // %1$=original expectation, %2$=thrown type, %3$=thrown message
        fr.put("connector.or", " ou ");
        fr.put("failed", "ÉCHEC DU TEST!");
        fr.put("passed", "TEST RÉUSSI AVEC SUCCÈS!");
        fr.put("expected", "%s était attendu"); // %1$=expected value
        fr.put("expected.result", "le résultat attendu était %s"); // %1$=expected value
        fr.put("obtained.result", "le résultat obtenu était %s"); // %1$=actual value
        fr.put("no.exception.basic", "exception attendue mais aucune n'a été levée. %s était attendu"); // %1$=expected exception description
        fr.put("wrong.exception.type.basic", "le test a levé l'exception %s"); // %1$=actual thrown type
        fr.put("wrong.exception.message.basic", "le test a levé le type d'exception attendu %s mais le message était %s"); // %1$=expected type, %2$=actual message
        fr.put("wrong.exception.and.message.basic", "le test a levé l'exception %s avec le message %s"); // %1$=actual type, %2$=actual message
        fr.put("exception.description", "l'exception %s"); // %1$=type name(s)
        fr.put("exception.with.message.description", "l'exception %s avec le message %s"); // %1$=type name(s), %2$=exact message
        fr.put("exception.with.predicate.description", "l'exception %s avec message satisfaisant : %s"); // %1$=type name(s), %2$=predicate help
        fr.put("exception.oneof.description", "une des exceptions %s"); // %1$=type name list
        fr.put("exception.oneof.with.message.description", "une des exceptions %s avec le message %s"); // %1$=type name list, %2$=exact message
        fr.put("exception.oneof.with.predicate.description", "une des exceptions %s avec message satisfaisant : %s"); // %1$=type name list, %2$=predicate help
        fr.put("exception.except.description", "toute exception sauf %s"); // %1$=excluded type name
        fr.put("exception.except.with.message.description", "toute exception sauf %s, avec le message %s"); // %1$=excluded type name, %2$=exact message
        fr.put("exception.except.with.predicate.description", "toute exception sauf %s, avec message satisfaisant : %s");  // %1$=excluded type name, %2$=predicate help
        fr.put("detail.expected_exact_message", "le message attendu était %s"); // %1$=exact message detail
        fr.put("detail.expected_predicate", "le message devait satisfaire : %s"); // %1$=predicate help detail
        fr.put("property.failure.base", "ne vérifie pas la propriété attendue"); // Base message for property failures
        fr.put("property.failure.suffix", " : %s"); // Suffix added when property description is available, %1$=property description
        fr.put("property.must.be.true", "doit être vraie"); // Help text for Assert
        fr.put("property.must.be.false", "doit être fausse"); // Help text for Refute
        fr.put("property.was.true", "la propriété était vraie"); // Result formatting for Assert/Refute failures
        fr.put("property.was.false", "la propriété était fausse"); // Result formatting for Assert/Refute failures
        fr.put("suite.for", "Tests pour %s"); // %1$=suite name
        fr.put("results.passed", "Réussis");
        fr.put("results.failed", "Échoués");
        fr.put("results.total", "Total");
        fr.put("results.detail", "Détail");
        fr.put("summary.title", "Résumé général");
        fr.put("summary.suites.run", "Suites exécutées : %d"); // %1$=number of suites
        fr.put("summary.total.tests", "Total des tests : %d"); // %1$=total tests
        fr.put("summary.success.rate", "Taux de réussite : %.2f%%"); // %1$=success rate percentage
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
