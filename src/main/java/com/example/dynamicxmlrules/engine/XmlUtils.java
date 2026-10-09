package com.example.dynamicxmlrules.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.example.dynamicxmlrules.domain.ExecutionContext;

/**
 * Utilitários de navegação DOM usados pelo motor e pelos handlers.
 */
public final class XmlUtils {

    private XmlUtils() {
    }

    public static List<Element> childElements(Element parent) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element child) {
                children.add(child);
            }
        }
        return children;
    }

    public static Element firstChild(Element parent, String tagName) {
        for (Element child : childElements(parent)) {
            if (tagName.equals(child.getTagName())) {
                return child;
            }
        }
        return null;
    }

    public static String requiredAttribute(Element element, String name) {
        if (!element.hasAttribute(name) || element.getAttribute(name).isBlank()) {
            throw new WorkflowExecutionException(
                    "Atributo obrigatório '" + name + "' ausente em <" + element.getTagName() + ">.");
        }
        return element.getAttribute(name);
    }

    public static String optionalAttribute(Element element, String name) {
        return element.hasAttribute(name) && !element.getAttribute(name).isBlank() ? element.getAttribute(name) : null;
    }

    /**
     * Lê os filhos {@code <param name="..." value="..."/>} resolvendo cada valor
     * no contexto (literal ou expressão SpEL).
     */
    public static Map<String, Object> parameters(Element element, ExecutionContext context) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (Element child : childElements(element)) {
            if (!"param".equals(child.getTagName())) {
                throw new WorkflowExecutionException(
                        "Tag <" + child.getTagName() + "> não é permitida dentro de <" + element.getTagName() + ">.");
            }
            String name = requiredAttribute(child, "name");
            params.put(name, context.resolveValue(child.getAttribute("value")));
        }
        return params;
    }

    /**
     * Converte um elemento em estrutura navegável por SpEL: elementos folha sem atributos
     * viram texto; os demais viram {@code Map} com atributos e filhos (repetidos viram lista).
     */
    public static Object toValue(Element element) {
        List<Element> children = childElements(element);
        NamedNodeMap attributes = element.getAttributes();
        String text = element.getTextContent() == null ? "" : element.getTextContent().strip();
        if (children.isEmpty() && attributes.getLength() == 0) {
            return text;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            map.put(attribute.getNodeName(), attribute.getNodeValue());
        }
        for (Element child : children) {
            Object value = toValue(child);
            map.merge(child.getTagName(), value, XmlUtils::appendToList);
        }
        if (children.isEmpty() && !text.isEmpty()) {
            map.put("text", text);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Object appendToList(Object existing, Object value) {
        List<Object> list;
        if (existing instanceof RepeatedElements<?>) {
            list = (List<Object>) existing;
        } else {
            list = new RepeatedElements<>();
            list.add(existing);
        }
        list.add(value);
        return list;
    }

    /** Marca listas criadas a partir de elementos repetidos (distinguindo-as de valores). */
    private static final class RepeatedElements<T> extends ArrayList<T> {
    }
}
