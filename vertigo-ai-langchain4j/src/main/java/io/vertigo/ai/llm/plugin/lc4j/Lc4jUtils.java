package io.vertigo.ai.llm.plugin.lc4j;

import dev.langchain4j.data.message.SystemMessage;
import io.vertigo.ai.llm.model.VPersona;

public class Lc4jUtils {

	private Lc4jUtils() {
		// private constructor
	}

	public static SystemMessage getSystemMessageFromPersona(final VPersona persona) {
		final StringBuilder personaInstructions = new StringBuilder();
		if (persona.name() != null) {
			personaInstructions.append("Your name is '" + persona.name() + "'.");
		}
		if (persona.role() != null) {
			if (personaInstructions.length() > 0) {
				personaInstructions.append("\n");
			}
			personaInstructions.append(persona.role());
		}
		if (persona.context() != null) {
			if (personaInstructions.length() > 0) {
				personaInstructions.append("\n");
			}
			personaInstructions.append(persona.context());
		}
		if (persona.style() != null) {
			if (personaInstructions.length() > 0) {
				personaInstructions.append("\n");
			}
			personaInstructions.append(persona.style());
		}
		return SystemMessage.from(personaInstructions.toString());
	}

}
