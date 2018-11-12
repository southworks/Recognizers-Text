package com.microsoft.recognizers.text.tests.datetime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.recognizers.text.Culture;
import com.microsoft.recognizers.text.ExtractResult;
import com.microsoft.recognizers.text.ModelResult;
import com.microsoft.recognizers.text.datetime.DateTimeOptions;
import com.microsoft.recognizers.text.datetime.english.extractors.EnglishDurationExtractorConfiguration;
import com.microsoft.recognizers.text.datetime.english.parsers.EnglishCommonDateTimeParserConfiguration;
import com.microsoft.recognizers.text.datetime.english.parsers.EnglishDateParserConfiguration;
import com.microsoft.recognizers.text.datetime.english.parsers.EnglishDurationParserConfiguration;
import com.microsoft.recognizers.text.datetime.extractors.BaseDurationExtractor;
import com.microsoft.recognizers.text.datetime.extractors.IDateTimeExtractor;
import com.microsoft.recognizers.text.datetime.parsers.*;
import com.microsoft.recognizers.text.datetime.utilities.DateTimeResolutionResult;
import com.microsoft.recognizers.text.datetime.utilities.TimeZoneResolutionResult;
import com.microsoft.recognizers.text.tests.AbstractTest;
import com.microsoft.recognizers.text.tests.TestCase;
import com.microsoft.recognizers.text.tests.helpers.DateTimeResolutionResultMixIn;
import com.microsoft.recognizers.text.tests.helpers.TimeZoneResolutionResultMixIn;
import org.javatuples.Pair;
import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DateTimeParserTest extends AbstractTest {

	private static final String recognizerType = "DateTime";

	@Parameterized.Parameters(name = "{0}")
	public static Collection<TestCase> testCases() {
			return AbstractTest.enumerateTestCases(recognizerType, "Parser");
	}

	public DateTimeParserTest(TestCase currentCase) {
		super(currentCase);
	}

	@Override
	protected List<ModelResult> recognize(TestCase currentCase) {
		return null;
	}

	protected List<DateTimeParseResult> parse(TestCase currentCase) {
		IDateTimeExtractor extractor = getExtractor(currentCase);
		IDateTimeParser parser = getParser(currentCase);
		LocalDateTime referenceDateTime = currentCase.getReferenceDateTime();
		List<ExtractResult> extractResult = extractor.extract(currentCase.input, referenceDateTime);
		return extractResult.stream().map(er -> parser.parse(er, referenceDateTime)).collect(Collectors.toList());
	}

	@Override
	protected void recognizeAndAssert(TestCase currentCase) {
		List<DateTimeParseResult> results = parse(currentCase);
		assertParseResults(currentCase, results);
	}

	public static void assertParseResults(TestCase currentCase, List<DateTimeParseResult> results) {
		List<DateTimeParseResult> expectedResults = readExpectedDateTimeParseResults(DateTimeParseResult.class, currentCase.results);
		Assert.assertEquals(getMessage(currentCase, "\"Result Count\""), expectedResults.size(), results.size());

		IntStream.range(0, expectedResults.size())
				.mapToObj(i -> Pair.with(expectedResults.get(i), results.get(i)))
				.forEach(t -> {
					DateTimeParseResult expected = t.getValue0();
					DateTimeParseResult actual = t.getValue1();

					Assert.assertEquals(getMessage(currentCase, "type"), expected.type, actual.type);
					Assert.assertEquals(getMessage(currentCase, "text"), expected.text, actual.text);
					Assert.assertEquals(getMessage(currentCase, "start"), expected.start, actual.start);
					Assert.assertEquals(getMessage(currentCase, "length"), expected.length, actual.length);

					if (expected.value != null) {
						DateTimeResolutionResult expectedValue = parseDateTimeResolutionResult(DateTimeResolutionResult.class, expected.value);
						DateTimeResolutionResult actualValue = (DateTimeResolutionResult) actual.value;

						Assert.assertEquals(getMessage(currentCase, "timex"), expectedValue.getTimex(), actualValue.getTimex());
						Assert.assertEquals(getMessage(currentCase, "futureResolution"), expectedValue.getFutureResolution(), actualValue.getFutureResolution());
						Assert.assertEquals(getMessage(currentCase, "pastResolution"), expectedValue.getPastResolution(), actualValue.getPastResolution());
					}
				});
	}

  	private static IDateTimeParser getParser(TestCase currentCase) {
		try {
			String culture = getCultureCode(currentCase.language);
			String name = currentCase.modelName;
			switch (culture) {
				case Culture.English:
					return getEnglishParser(name);
				default:
					throw new AssumptionViolatedException("Parser Type/Name not supported.");
			}
		} catch (IllegalArgumentException ex) {
			throw new AssumptionViolatedException(ex.getMessage(), ex);
		}
	}

	private static IDateTimeParser getEnglishParser(String name) {
		switch (name) {
			case "TimeZoneParser":
				return new BaseTimeZoneParser();
			case "DurationParser":
				return new BaseDurationParser(new EnglishDurationParserConfiguration(new EnglishCommonDateTimeParserConfiguration(DateTimeOptions.None)));
			case "DateParser":
				return new BaseDateParser(new EnglishDateParserConfiguration(new EnglishCommonDateTimeParserConfiguration(DateTimeOptions.None)));
			default:
				throw new AssumptionViolatedException("Parser Type/Name not supported.");
		}
	}

	private IDateTimeExtractor getExtractor(TestCase currentCase) {
		String extractorName = currentCase.modelName.replace("Parser", "Extractor");
		return DateTimeExtractorTest.getExtractor(currentCase.language, extractorName);
	}

	public static <T extends DateTimeResolutionResult> T parseDateTimeResolutionResult(Class<T> dateTimeResolutionResultClass, Object result) {
		// Deserializer
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
		mapper.addMixIn(DateTimeResolutionResult.class, DateTimeResolutionResultMixIn.class);
		mapper.addMixIn(TimeZoneResolutionResult.class, TimeZoneResolutionResultMixIn.class);

		try {
			String json = mapper.writeValueAsString(result);
			return mapper.readValue(json, dateTimeResolutionResultClass);

		} catch (JsonProcessingException e) {
			e.printStackTrace();
			return null;

		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}