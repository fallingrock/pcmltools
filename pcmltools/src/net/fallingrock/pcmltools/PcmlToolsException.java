/*
    This file is part of PCMLTOOLS.

    PCMLTOOLS is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    PCMLTOOLS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with PCMLTOOLS.  If not, see <https://www.gnu.org/licenses/>.

 */
package net.fallingrock.pcmltools;

public class PcmlToolsException extends Exception {

	/**
	 * Exception thrown by PcmlTools when a problem occurs.
	 */
	private static final long serialVersionUID = 1L;

	public PcmlToolsException(String message) {
		super(message);
	}

	public PcmlToolsException(Throwable cause) {
		super(cause);
	}

}
