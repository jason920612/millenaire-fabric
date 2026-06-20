package org.millenaire.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Foundation for the Millénaire GUI text framework (INTENT.md doc 05, §2).
 *
 * <p>The original UI is built on one shared model: a screen is a list of <b>pages</b>, each page a
 * list of <b>lines</b>, each line a sequence of styled <b>segments</b>, authored with an inline
 * {@code <color>}/{@code <shadow>} tag DSL, then word-wrapped and paginated automatically.
 * Reputation-gated "progressive disclosure" buttons etc. live on top of this model.
 *
 * <p>This is a <b>pure data model</b> with no Minecraft client dependencies, so it compiles and is
 * unit-testable today. Actual rendering against 26.2's new {@code extractRenderState} pipeline
 * (which replaced {@code render(GuiGraphics, …)}) is deferred to L5 (GUI layer).
 */
public final class GuiText {

	/** Logical colours from the original tag DSL; mapped to concrete ARGB at render time (L5). */
	public enum Color {
		DEFAULT, BLACK, DARKGREEN, GREEN, DARKRED, RED, BLUE, DARKBLUE, GOLD, YELLOW, GRAY, DARKGRAY, WHITE
	}

	/** A run of text with a single style. */
	public record Segment(String text, Color color, boolean shadow) {
		public static Segment of(String text) {
			return new Segment(text, Color.DEFAULT, false);
		}
	}

	/** One visual line: an ordered list of styled segments. */
	public record Line(List<Segment> segments) {
		public static Line of(String rawWithTags) {
			return new Line(parseTags(rawWithTags));
		}

		public String plain() {
			StringBuilder sb = new StringBuilder();
			for (Segment s : segments) {
				sb.append(s.text());
			}
			return sb.toString();
		}
	}

	private final List<List<Line>> pages = new ArrayList<>();

	public GuiText() {
		pages.add(new ArrayList<>());
	}

	/** Append a line (parsing inline {@code <color>}/{@code <shadow>} tags) to the current page. */
	public GuiText line(String rawWithTags) {
		currentPage().add(Line.of(rawWithTags));
		return this;
	}

	public GuiText line(Line line) {
		currentPage().add(line);
		return this;
	}

	/** Start a new page. */
	public GuiText newPage() {
		pages.add(new ArrayList<>());
		return this;
	}

	public List<List<Line>> pages() {
		return pages;
	}

	public int pageCount() {
		return pages.size();
	}

	private List<Line> currentPage() {
		return pages.get(pages.size() - 1);
	}

	/**
	 * Re-flow a flat list of lines into pages of at most {@code linesPerPage}, mirroring the
	 * original auto-pagination. (Word-wrap by pixel width is a render-time concern, added in L5.)
	 */
	public static GuiText paginate(List<Line> lines, int linesPerPage) {
		GuiText out = new GuiText();
		int onPage = 0;
		for (Line line : lines) {
			if (onPage == linesPerPage) {
				out.newPage();
				onPage = 0;
			}
			out.line(line);
			onPage++;
		}
		return out;
	}

	/**
	 * Parse the inline tag DSL into styled segments. Supported: {@code <color>} (e.g.
	 * {@code <darkgreen>}) sets the colour for following text; {@code <shadow>} toggles shadow.
	 * Unknown tags are emitted as literal text so nothing is silently lost.
	 */
	static List<Segment> parseTags(String raw) {
		List<Segment> out = new ArrayList<>();
		Color color = Color.DEFAULT;
		boolean shadow = false;
		StringBuilder buf = new StringBuilder();

		for (int i = 0; i < raw.length(); ) {
			char c = raw.charAt(i);
			if (c == '<') {
				int close = raw.indexOf('>', i);
				if (close > i) {
					String tag = raw.substring(i + 1, close).trim().toLowerCase(Locale.ROOT);
					Color parsed = tryColor(tag);
					if (parsed != null) {
						flush(out, buf, color, shadow);
						color = parsed;
						i = close + 1;
						continue;
					}
					if (tag.equals("shadow")) {
						flush(out, buf, color, shadow);
						shadow = true;
						i = close + 1;
						continue;
					}
					if (tag.equals("/shadow")) {
						flush(out, buf, color, shadow);
						shadow = false;
						i = close + 1;
						continue;
					}
				}
				// not a recognised tag — keep '<' literally
			}
			buf.append(c);
			i++;
		}
		flush(out, buf, color, shadow);
		return out;
	}

	private static Color tryColor(String tag) {
		for (Color col : Color.values()) {
			if (col.name().toLowerCase(Locale.ROOT).equals(tag)) {
				return col;
			}
		}
		return null;
	}

	private static void flush(List<Segment> out, StringBuilder buf, Color color, boolean shadow) {
		if (buf.length() > 0) {
			out.add(new Segment(buf.toString(), color, shadow));
			buf.setLength(0);
		}
	}
}
