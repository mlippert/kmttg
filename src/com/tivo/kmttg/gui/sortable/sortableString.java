/*
 * Copyright 2008-Present Kevin Moye <moyekj@yahoo.com>.
 *
 * This file is part of kmttg package.
 *
 * kmttg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tivo.kmttg.gui.sortable;

import com.tivo.kmttg.JSON.JSONObject;

public class sortableString {
   public String display;
   public JSONObject json;
   
   public sortableString() {
      json = new JSONObject();
      display = "";
   }
   
   public sortableString(JSONObject json, String s) {
      this.json = json;
      display = s;
   }
   
   public String toString() {
      return display;
   }
}
