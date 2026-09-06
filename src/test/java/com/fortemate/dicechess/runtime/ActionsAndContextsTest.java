package com.fortemate.dicechess.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActionsAndContextsTest {

	@Test
	void turnActionCopiesMovesAndIsImmutable() {
		List<String> source = new ArrayList<>(List.of("e2e4"));
		TurnAction action = new TurnAction(source);

		source.add("e7e5");
		assertThat(action.moves()).containsExactly("e2e4");

		assertThatThrownBy(() -> action.moves().add("e7e5"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void turnActionRejectsNullMovesAndNullElement() {
		assertThatThrownBy(() -> new TurnAction(null))
				.isInstanceOf(NullPointerException.class);

		List<String> movesWithNull = Arrays.asList("e2e4", null);
		assertThatThrownBy(() -> new TurnAction(movesWithNull))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void turnActionOneArgConstructorDefaultsOfferDrawToFalse() {
		TurnAction action = new TurnAction(List.of("e2e4"));
		assertThat(action.offerDraw()).isFalse();
	}

	@Test
	void drawActionFactoryMethods() {
		assertThat(DrawAction.accept().acceptDraw()).isTrue();
		assertThat(DrawAction.decline().acceptDraw()).isFalse();
	}

	@Test
	void doubleOfferActionFactoryMethodsAndSynonymEquivalence() {
		assertThat(DoubleOfferAction.offer().offerDouble()).isTrue();
		assertThat(DoubleOfferAction.roll().offerDouble()).isFalse();
		assertThat(DoubleOfferAction.decline().offerDouble()).isFalse();

		assertThat(DoubleOfferAction.decline()).isEqualTo(DoubleOfferAction.roll());
	}

	@Test
	void doubleResponseActionFactoryMethodsAndSynonymEquivalence() {
		assertThat(DoubleResponseAction.accept().acceptDouble()).isTrue();
		assertThat(DoubleResponseAction.decline().acceptDouble()).isFalse();
		assertThat(DoubleResponseAction.drop().acceptDouble()).isFalse();

		assertThat(DoubleResponseAction.drop()).isEqualTo(DoubleResponseAction.decline());
	}

	@Test
	void turnContextValidatesBlankGameIdAndDfen() {
		assertThatThrownBy(() -> new TurnContext(null, "White", 1L, "dfen", null, null, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("gameId must not be blank");

		assertThatThrownBy(() -> new TurnContext("  ", "White", 1L, "dfen", null, null, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("gameId must not be blank");

		assertThatThrownBy(() -> new TurnContext("game-1", "White", 1L, null, null, null, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("dfen must not be blank");

		assertThatThrownBy(() -> new TurnContext("game-1", "White", 1L, "", null, null, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("dfen must not be blank");
	}

	@Test
	void turnContextValidatesSeat() {
		assertThatThrownBy(() -> new TurnContext("game-1", null, 1L, "dfen", null, null, false))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new TurnContext("game-1", "Red", 1L, "dfen", null, null, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("seat must be White or Black");
	}

	@Test
	void turnContextAcceptsNullLegalMoves() {
		TurnContext context = new TurnContext("game-1", "White", 1L, "dfen", null, null, false);
		assertThat(context.legalMoves()).isNull();
	}

	@Test
	void turnContextRejectsNullPathInsideLegalMoves() {
		List<List<String>> legalMovesWithNullPath = Arrays.asList(List.of("e2e4"), null);
		assertThatThrownBy(() -> new TurnContext("game-1", "White", 1L, "dfen", null, legalMovesWithNullPath, false))
				.isInstanceOf(NullPointerException.class)
				.hasMessage("legal move path must not be null");
	}

	@Test
	void turnContextDeepCopiesLegalMovesAndEnsuresImmutability() {
		List<String> path1 = new ArrayList<>(List.of("e2e4", "e7e5"));
		List<List<String>> outerList = new ArrayList<>();
		outerList.add(path1);

		TurnContext context = new TurnContext("game-1", "Black", 2L, "dfen", null, outerList, true);

		// Mutate source lists
		path1.add("g1f3");
		outerList.add(new ArrayList<>(List.of("d2d4")));

		// Confirm context's copy is unmodified
		assertThat(context.legalMoves()).containsExactly(List.of("e2e4", "e7e5"));

		// Confirm context's returned lists are immutable
		assertThatThrownBy(() -> context.legalMoves().add(List.of("d2d4")))
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> context.legalMoves().get(0).add("g1f3"))
				.isInstanceOf(UnsupportedOperationException.class);
	}
}
