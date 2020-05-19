/*
Copyright 2020-2020 Cedric Liegeois

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

    * Neither the name of the copyright holder nor the names of other
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package io.omam.peel;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

@SuppressWarnings("javadoc")
final class SvgParser {

    private SvgParser() {
        // empty.
    }

    static Region parse(final InputStream input, final double scale) {
        final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        builderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try {
            final DocumentBuilder builder = builderFactory.newDocumentBuilder();
            final Document document = builder.parse(input);
            final Element rootElement = document.getDocumentElement();
            if (!rootElement.getNodeName().equalsIgnoreCase("svg")) {
                throw new IllegalArgumentException("Not an SVG document");
            }
            final StackPane pane = new StackPane();
            setSize(pane, rootElement, scale);
            final Collection<SVGPath> paths = parsePaths(rootElement.getChildNodes(), scale);
            if (paths.isEmpty()) {
                throw new IllegalArgumentException("No SVG path");
            }
            pane.getChildren().addAll(paths);
            return pane;
        } catch (final ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Optional<SVGPath> parsePath(final Element elt, final double scale) {
        final String fill = elt.getAttribute("fill");
        if (fill.equalsIgnoreCase("none")) {
            return Optional.empty();
        }
        final SVGPath path = new SVGPath();
        path.getStyleClass().add("shape");
        if (!fill.isEmpty()) {
            path.setFill(Paint.valueOf(fill));
        }
        path.setScaleX(scale);
        path.setScaleY(scale);

        final String fillRule = elt.getAttribute("fill-rule");
        if (!fillRule.isEmpty()) {
            if (fillRule.equalsIgnoreCase("evenodd")) {
                path.setFillRule(FillRule.EVEN_ODD);
            } else {
                path.setFillRule(FillRule.NON_ZERO);
            }
        }

        final String d = elt.getAttribute("d");
        if (d.isEmpty()) {
            throw new IllegalArgumentException("Missing \"d\" attribute");
        }
        path.setContent(d);
        return Optional.of(path);

    }

    private static Collection<SVGPath> parsePaths(final NodeList nodes, final double scale) {
        final Collection<SVGPath> paths = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            final Node node = nodes.item(i);
            if (node.getNodeName().equalsIgnoreCase("path")) {
                parsePath((Element) node, scale).ifPresent(paths::add);
            }
        }
        return paths;
    }

    private static void setSize(final Region region, final Element elt, final double scale) {
        try {
            final int width = Integer.parseInt(elt.getAttribute("width"));
            final int height = Integer.parseInt(elt.getAttribute("height"));
            region.setPrefWidth(width * scale);
            region.setPrefHeight(height * scale);
        } catch (final NumberFormatException e) {
            // ignore.
        }

    }

}
