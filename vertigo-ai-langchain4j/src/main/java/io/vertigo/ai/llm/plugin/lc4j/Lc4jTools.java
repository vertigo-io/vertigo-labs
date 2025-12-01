package io.vertigo.ai.llm.plugin.lc4j;

import java.math.BigDecimal;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.vertigo.commons.peg.PegNoMatchFoundException;
import io.vertigo.commons.peg.PegParsingValueException;
import io.vertigo.commons.peg.PegSolver;
import io.vertigo.commons.peg.PegSolver.PegSolverFunction;
import io.vertigo.commons.peg.rule.PegRule;
import io.vertigo.commons.peg.rule.PegRules;
import io.vertigo.commons.peg.rule.PegWordRuleMode;
import io.vertigo.commons.peg.term.PegArithmeticsOperatorTerm;

public class Lc4jTools {

	private static final PegRule<PegSolver<String, Object, Object>> RULE = PegRules.delayedOperation(PegRules.word(false, "-0123456789.", PegWordRuleMode.ACCEPT, "0-9"),
			PegArithmeticsOperatorTerm.class, false);
	private static final PegSolverFunction<String, Object> TERM_PARSER = BigDecimal::new;

	@Tool("Returns the computation of the mathematical expression. At least 1 operation is mandatory in the expression. It handles addition, substraction, multiplication and division. It does handle parenthesis. It can be used to compute amount, tax values, etc. Example, you can compute '2 + 2' or '(2 + 2) * 2'.You can also substract 20% taxes with '100 / 1.2', but be careful to round afterward. ")
	public BigDecimal compute(@P("The mathematical expression to compute") final String expression) throws PegNoMatchFoundException, PegParsingValueException {
		return evaluate(expression);
	}

	/**
	 * '(amount * taxRate) / (1 + taxRate)'
	 */
	@Tool("Returns the tax from an amount including taxes.")
	public BigDecimal computeTax(@P("The amount including taxes") final BigDecimal amount, @P("The percentage of taxes, eg 0.20 for 20%") final BigDecimal taxPercentage)
			throws PegNoMatchFoundException, PegParsingValueException {
		return evaluate("(" + amount + " * " + taxPercentage + ") / (1 + " + taxPercentage + ")");
	}

	private static BigDecimal evaluate(final String expression) throws PegNoMatchFoundException, PegParsingValueException {
		return (BigDecimal) RULE.parse(expression).value().apply(TERM_PARSER);
	}

}
