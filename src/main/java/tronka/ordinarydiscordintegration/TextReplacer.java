package tronka.ordinarydiscordintegration;

import eu.pb4.placeholders.api.node.LiteralNode;
import eu.pb4.placeholders.api.node.TextNode;
import eu.pb4.placeholders.api.node.parent.ClickActionNode;
import eu.pb4.placeholders.api.parsers.NodeParser;
import eu.pb4.placeholders.api.parsers.ParserBuilder;
import eu.pb4.placeholders.api.parsers.TagLikeParser;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextReplacer {
    private final ParserBuilder builder;
    private final Map<String, TextNode> placeholders;
    private TagLikeParser.Format format;

    private TextReplacer(TagLikeParser.Format format) {
        this.placeholders = new HashMap<>();
        this.builder = NodeParser.builder()
                .globalPlaceholders()
                .placeholders(format, this.placeholders::get)
                .simplifiedTextFormat();
    }

    public TextReplacer replace(String key, String value) {
        return replace(key, TextNode.of(value));
    }


    public TextReplacer replace(String key, TextNode node) {
        this.placeholders.put(key, node);
        return this;
    }

    private NodeParser getParser() {
        return this.builder.build();
    }

    public Text apply(String text) {
        return getParser().parseNode(text).toText();
    }

    public TextNode applyNode(String text) {
        return getParser().parseNode(text);
    }

    public static TextReplacer create() {
        return new TextReplacer(TagLikeParser.PLACEHOLDER);
    }

    public static TextReplacer create(TagLikeParser.Format format) {
        return new TextReplacer(format);
    }
}
