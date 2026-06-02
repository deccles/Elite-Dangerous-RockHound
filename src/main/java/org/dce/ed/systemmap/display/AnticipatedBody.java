package org.dce.ed.systemmap.display;

/**
 * FSS discovery-only body (name known, no Scan yet). Never part of model graph.
 */
public record AnticipatedBody(String designation, double worldX, double worldY) {
}
