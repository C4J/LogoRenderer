package com.commander4j.logorenderer;

/**
 * Callback interface for diagnostic logging during LLF parsing.
 * Implementations receive structured messages about each line parsed,
 * option flags decoded, and any warnings or errors encountered.
 */
public interface ParseLogger
{

	/** Log an informational message (normal parsing). */
	void info(String message);

	/** Log a warning (unexpected but recoverable). */
	void warn(String message);

	/** Log an error (parsing failure). */
	void error(String message);
}
