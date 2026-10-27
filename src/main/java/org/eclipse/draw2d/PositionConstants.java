/*******************************************************************************
 * Copyright (c) 2000, 2010 IBM Corporation and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.draw2d;

/**
 * Minimal stand-in for Draw2D's PositionConstants (constants only), so the
 * vendored {@code geometry} and {@code graph} packages compile without the
 * rest of Draw2D/SWT.
 */
public interface PositionConstants {
	int NONE = 0;
	int LEFT = 1;
	int CENTER = 2;
	int RIGHT = 4;
	int LEFT_CENTER_RIGHT = LEFT | CENTER | RIGHT;
	int ALWAYS_LEFT = 64;
	int ALWAYS_RIGHT = 128;
	int TOP = 8;
	int MIDDLE = 16;
	int BOTTOM = 32;
	int TOP_MIDDLE_BOTTOM = TOP | MIDDLE | BOTTOM;
	int NORTH = 1;
	int SOUTH = 4;
	int WEST = 8;
	int EAST = 16;
	int HORIZONTAL = 64;
	int VERTICAL = 128;
	int NORTH_EAST = NORTH | EAST;
	int NORTH_WEST = NORTH | WEST;
	int SOUTH_EAST = SOUTH | EAST;
	int SOUTH_WEST = SOUTH | WEST;
	int NORTH_SOUTH = NORTH | SOUTH;
	int EAST_WEST = EAST | WEST;
	int NSEW = NORTH_SOUTH | EAST_WEST;
}
