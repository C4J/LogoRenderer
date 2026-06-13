package com.commander4j.logorenderer;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates UVAR formula strings against the current field values in an
 * {@link LlfLayout}.
 *
 * Supported substitution tokens:
 *
 * %NF — full value of field N %(N,len,L)$ — field N, left-padded with spaces to
 * len chars %(N,len,R)$ — field N, right-padded with spaces to len chars
 * %(N,start,len)$ — substring of field N: chars start..start+len (1-based
 * start) %D — day (DD) %M — month (MM) %y — year (YY two-digit) %0a20%y —
 * century prefix "20" then two-digit year (= full 4-digit year) %h — hour (HH)
 * %m — minute (MM) %0B — literal space (used as separator in date format) %0b —
 * literal empty (used to terminate date token sequences)
 *
 * Unknown tokens are left as-is.
 */
public class UvarEvaluator
{

	// Matches %(fieldId, arg2, arg3)$
	private static final Pattern PAREN_TOKEN = Pattern.compile("%\\((\\d+),(\\d+),([LR\\d]+)\\)\\$");

	// Matches %NF (field substitution — e.g. %451F)
	private static final Pattern FIELD_TOKEN = Pattern.compile("%(\\d+)F");

	// Single combined regex for date/time tokens and counter/special refs.
	// Ordered so more specific patterns match first:
	//   group 1: %Ny — last N digits of the 4-digit year (e.g. %1y=6, %2y=26)
	//   group 2: %NC — counter/special ref (non-date letters, stripped to "")
	//   group 3: single-letter date/time tokens
	//   group 4: special %0-prefixed literals (%0B=space, %0b=empty, %0a20="20")
	// A single pass means substituted values cannot shadow later tokens.
	private static final Pattern DATE_OR_COUNTER = Pattern.compile(
			"%(\\d+)y"
			+ "|%(\\d+)[A-EG-Z]"
			+ "|%([DMYyJhmsn])"
			+ "|%(0B|0b|0a20)");

	// Matches %(odd,even,mod,op)U or %(odd,even,mod)U — Universal Check Digit.
	// Computes a check digit over the digits already produced earlier in the
	// VAR result string (i.e. data preceding this token after prior
	// substitutions have run).
	private static final Pattern CHECK_DIGIT_TOKEN = Pattern.compile("%\\((\\d+),(\\d+),(\\d+)(?:,([NRS]))?\\)U");

	// Matches %(...)X / %(...)r / %(...)G / %(...)i — customised ops, RFID,
	// graphic, image refs that have no meaningful value in the static viewer.
	// Stripped to empty so they don't survive into rendered output.
	private static final Pattern PAREN_STRIP = Pattern.compile("%\\([^)]*\\)[XrGi]");

	// Date-shift X tokens that MODIFY the effective date before subsequent
	// %M / %Y / %D / %7X tokens are evaluated. Matched in formula order; each
	// match is consumed (replaced with empty) so the current-date-driven
	// tokens that follow pick up the shift.
	//   kind=10 → add %field days, kind=11 → add %field months,
	//   kind=12 → add %field years, kind=13 → add months (day capped to 25),
	//   kind=26 → add %field months, no day overflow
	// All use java.time, whose plusMonths/plusYears already cap the day to the
	// last valid day of the target month — matching the "no overflow" semantic
	// documented for %(26,n)X.
	private static final Pattern DATE_SHIFT = Pattern.compile("%\\((10|11|12|13|26),(\\d+)\\)X");

	/**
	 * Evaluate the formula for the given UVAR id and return the result string.
	 * Returns empty string if the UVAR id is not defined.
	 */
	public String evaluate(int uvarId, LlfLayout layout)
	{
		String formula = layout.getUvarFormula(uvarId);
		if (formula == null)
			return "";
		return evaluateFormula(formula, layout.getFields());
	}

	/**
	 * Resolves every VAR in the layout and writes each result back into the
	 * layout's field map, so VARs that reference other VARs via {@code %NF}
	 * pick up the evaluated value. Iterates until stable (max {@value #MAX_PASSES}
	 * passes to break potential cycles).
	 *
	 * <p>Run this before reading an FL-linked field at print time or render time
	 * when the layout has nested VAR dependencies (e.g. {@code VAR,1950,%420F...}
	 * where field 420 is itself a VAR output, not an FD value).
	 */
	public void resolveAll(LlfLayout layout)
	{
		if (layout == null)
			return;
		Map<Integer, String> uvars = layout.getUvars();
		for (int pass = 0; pass < MAX_PASSES; pass++)
		{
			boolean changed = false;
			for (Map.Entry<Integer, String> entry : uvars.entrySet())
			{
				int id = entry.getKey();
				String newValue = evaluateFormula(entry.getValue(), layout.getFields());
				if (!newValue.equals(layout.getField(id)))
				{
					layout.setField(id, newValue);
					changed = true;
				}
			}
			if (!changed)
				break;
		}
	}

	private static final int MAX_PASSES = 10;

	/**
	 * Evaluate a formula string directly against a field map.
	 */
	public String evaluateFormula(String formula, Map<Integer, String> fields)
	{
		if (formula == null || formula.isEmpty())
			return "";

		String result = formula;

		// 1. Replace %(N,arg2,arg3)$ tokens
		result = replaceParen(result, fields);

		// 2. Replace %NF tokens
		result = replaceFields(result, fields);

		// 3. Consume date-shift X tokens (e.g. %(26,15)X) and compute the
		// effective date that the following %M/%Y/%D/%7X tokens should use.
		// This MUST run before replaceDateAndCounterTokens so downstream date
		// substitutions see the shifted date, not "now".
		ShiftResult shifted = applyDateShifts(result, fields);
		result = shifted.formula;

		// 4. Replace date/time tokens AND %NC counter/special tokens in a
		// single pass so that substituted digits cannot shadow later tokens
		// (e.g. "%1y%J" must not become "6%J" and then fail to match %J).
		result = replaceDateAndCounterTokens(result, shifted.date);

		// 5. Strip unsupported %(...)X / %(...)r / %(...)G / %(...)i tokens
		result = PAREN_STRIP.matcher(result).replaceAll("");

		// 6. Compute Universal Check Digit %(odd,even,mod,op)U LAST — it
		// operates on the digits already produced by the preceding tokens.
		result = replaceCheckDigit(result);

		return result;
	}

	/** Result of the date-shift pass: the formula with shift tokens removed, plus the effective date. */
	private static final class ShiftResult
	{
		final String formula;
		final LocalDateTime date;
		ShiftResult(String formula, LocalDateTime date)
		{
			this.formula = formula;
			this.date    = date;
		}
	}

	/**
	 * Walk the formula left-to-right, applying each {@code %(kind,n)X} shift
	 * token in order to a mutable {@link LocalDateTime} starting from now.
	 * Each shift is removed from the output formula. Non-shift substrings
	 * are copied through unchanged.
	 */
	private ShiftResult applyDateShifts(String input, Map<Integer, String> fields)
	{
		LocalDateTime date = LocalDateTime.now();
		Matcher m = DATE_SHIFT.matcher(input);
		StringBuilder sb = new StringBuilder();
		while (m.find())
		{
			int kind     = Integer.parseInt(m.group(1));
			int fieldArg = Integer.parseInt(m.group(2));
			int amount   = parseIntSafe(fields.getOrDefault(fieldArg, ""), 0);

			switch (kind)
			{
				case 10 -> date = date.plusDays(amount);
				case 11, 26 -> date = date.plusMonths(amount);
				case 12 -> date = date.plusYears(amount);
				case 13 -> {
					date = date.plusMonths(amount);
					if (date.getDayOfMonth() > 25)
						date = date.withDayOfMonth(25);
				}
			}
			m.appendReplacement(sb, "");
		}
		m.appendTail(sb);
		return new ShiftResult(sb.toString(), date);
	}

	private static int parseIntSafe(String s, int def)
	{
		if (s == null || s.isBlank()) return def;
		try { return Integer.parseInt(s.trim()); }
		catch (NumberFormatException e) { return def; }
	}

	// -------------------------------------------------------------------------

	private String replaceParen(String input, Map<Integer, String> fields)
	{
		Matcher m = PAREN_TOKEN.matcher(input);
		StringBuilder sb = new StringBuilder();
		while (m.find())
		{
			int fieldId = Integer.parseInt(m.group(1));
			int arg2 = Integer.parseInt(m.group(2));
			String arg3 = m.group(3);
			String value = fields.getOrDefault(fieldId, "");
			String replaced;

			// Token format: %(N, length, L|R|startPos)$
			// L → take leftmost `length` chars of field N
			// R → take rightmost `length` chars of field N
			// startPos → take `length` chars starting at 1-based position
			// startPos
			int length = arg2;
			if (arg3.equals("L"))
			{
				replaced = value.length() <= length ? value : value.substring(0, length);
			}
			else if (arg3.equals("R"))
			{
				replaced = value.length() <= length ? value : value.substring(value.length() - length);
			}
			else
			{
				try
				{
					int start0 = Integer.parseInt(arg3) - 1; // convert to
																// 0-based
					if (start0 < 0)
						start0 = 0;
					if (start0 >= value.length())
					{
						replaced = "";
					}
					else
					{
						int end = Math.min(start0 + length, value.length());
						replaced = value.substring(start0, end);
					}
				}
				catch (NumberFormatException e)
				{
					replaced = value;
				}
			}
			m.appendReplacement(sb, Matcher.quoteReplacement(replaced));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private String replaceFields(String input, Map<Integer, String> fields)
	{
		Matcher m = FIELD_TOKEN.matcher(input);
		StringBuilder sb = new StringBuilder();
		while (m.find())
		{
			int fieldId = Integer.parseInt(m.group(1));
			String value = fields.getOrDefault(fieldId, "");
			m.appendReplacement(sb, Matcher.quoteReplacement(value));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private String replaceDateAndCounterTokens(String input, LocalDateTime now)
	{
		String dd   = String.format("%02d", now.getDayOfMonth());
		String mm   = String.format("%02d", now.getMonthValue());
		String yy   = String.format("%02d", now.getYear() % 100);
		String yyyy = String.format("%04d", now.getYear());
		String jjj  = String.format("%03d", now.getDayOfYear());
		String hh   = String.format("%02d", now.getHour());
		String mi   = String.format("%02d", now.getMinute());
		String ss   = String.format("%02d", now.getSecond());
		String mon  = now.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
		String lastDay = String.format("%02d", YearMonth.from(now).lengthOfMonth());

		Matcher m = DATE_OR_COUNTER.matcher(input);
		StringBuilder sb = new StringBuilder();
		while (m.find())
		{
			String replacement;
			if (m.group(1) != null)
			{
				// %Ny — last N digits of the 4-digit year
				int n = Integer.parseInt(m.group(1));
				replacement = n >= yyyy.length() ? yyyy : yyyy.substring(yyyy.length() - n);
			}
			else if (m.group(2) != null)
			{
				// %NC counter/special ref — mostly stripped, with documented
				// exceptions that produce real values in the viewer:
				//   %7X → last day of the (possibly shifted) current month
				String orig = m.group();
				if ("%7X".equals(orig))
					replacement = lastDay;
				else
					replacement = "";
			}
			else if (m.group(3) != null)
			{
				replacement = switch (m.group(3))
				{
				case "D" -> dd;
				case "M" -> mm;
				case "Y" -> yyyy;
				case "y" -> yy;
				case "J" -> jjj;
				case "h" -> hh;
				case "m" -> mi;
				case "s" -> ss;
				case "n" -> mon;
				default -> m.group();
				};
			}
			else if (m.group(4) != null)
			{
				replacement = switch (m.group(4))
				{
				case "0B"   -> " ";
				case "0b"   -> "";
				case "0a20" -> "20";
				default     -> m.group();
				};
			}
			else
			{
				replacement = m.group();
			}
			m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private String replaceCheckDigit(String input)
	{
		Matcher m = CHECK_DIGIT_TOKEN.matcher(input);
		StringBuilder sb = new StringBuilder();
		int last = 0;
		while (m.find())
		{
			sb.append(input, last, m.start());
			int odd = Integer.parseInt(m.group(1));
			int even = Integer.parseInt(m.group(2));
			int mod = Integer.parseInt(m.group(3));
			String op = m.group(4);
			String digits = extractTrailingDigits(sb.toString());
			sb.append(computeCheckDigit(digits, odd, even, mod, op));
			last = m.end();
		}
		sb.append(input, last, input.length());
		return sb.toString();
	}

	private String extractTrailingDigits(String s)
	{
		int i = s.length();
		while (i > 0 && Character.isDigit(s.charAt(i - 1)))
			i--;
		return s.substring(i);
	}

	private String computeCheckDigit(String digits, int odd, int even, int mod, String op)
	{
		if (digits.isEmpty() || mod <= 0)
			return "0";

		boolean rightToLeft = "R".equals(op) || op == null;
		boolean sumDigitsOfProduct = "S".equals(op);

		int sum = 0;
		int len = digits.length();
		for (int i = 0; i < len; i++)
		{
			int posFromRight = len - i;
			int positionIndex = rightToLeft ? posFromRight : (i + 1);
			int weight = (positionIndex % 2 == 1) ? odd : even;
			int digit = digits.charAt(i) - '0';
			int product = digit * weight;
			if (sumDigitsOfProduct)
			{
				int s = 0;
				while (product > 0)
				{
					s += product % 10;
					product /= 10;
				}
				sum += s;
			}
			else
			{
				sum += product;
			}
		}

		if ("N".equals(op))
			return String.valueOf(sum % mod);
		int cd = (mod - (sum % mod)) % mod;
		return String.valueOf(cd);
	}

	@SuppressWarnings("unused")
	private String padLeft(String value, int totalWidth)
	{
		if (value.length() >= totalWidth)
			return value;
		return " ".repeat(totalWidth - value.length()) + value;
	}

	@SuppressWarnings("unused")
	private String padRight(String value, int totalWidth)
	{
		if (value.length() >= totalWidth)
			return value;
		return value + " ".repeat(totalWidth - value.length());
	}
}
