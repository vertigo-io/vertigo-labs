package io.vertigo.ai.llm.plugin.lc4j;

import java.util.Optional;

import dev.langchain4j.data.message.SystemMessage;
import io.vertigo.ai.llm.model.VPromptContext;
import io.vertigo.core.util.StringUtil;

public class Lc4jUtils {

	private Lc4jUtils() {
		// private constructor
	}

	public static Optional<SystemMessage> getSystemMessageFromContext(final VPromptContext context) {
		final var persona = context.getPersona();
		final var userPersona = context.getUserPersona();
		if (persona == null && userPersona == null) {
			return Optional.empty();
		}

		final StringBuilder personaInstructions = new StringBuilder();
		if (persona != null) {
			if (!StringUtil.isBlank(persona.name())) {
				personaInstructions.append("Your name is '" + persona.name() + "'.");
			}
			if (!StringUtil.isBlank(persona.role())) {
				if (personaInstructions.length() > 0) {
					personaInstructions.append("\n");
				}
				personaInstructions.append(persona.role());
			}
			if (!StringUtil.isBlank(persona.context())) {
				if (personaInstructions.length() > 0) {
					personaInstructions.append("\n");
				}
				personaInstructions.append(persona.context());
			}
			if (!StringUtil.isBlank(persona.style())) {
				if (personaInstructions.length() > 0) {
					personaInstructions.append("\n");
				}
				personaInstructions.append(persona.style());
			}
		}

		if (userPersona != null && !StringUtil.isBlank(userPersona.name())) {
			if (personaInstructions.length() > 0) {
				personaInstructions.append("\n");
			}
			personaInstructions.append("You are talking to '" + userPersona.name() + "'.");
		}
		return Optional.of(SystemMessage.from(personaInstructions.toString()));
	}

}
