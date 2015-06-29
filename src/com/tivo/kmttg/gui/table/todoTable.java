package com.tivo.kmttg.gui.table;

import java.util.Collections;
import java.util.Hashtable;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;

import com.tivo.kmttg.JSON.JSONArray;
import com.tivo.kmttg.JSON.JSONException;
import com.tivo.kmttg.JSON.JSONObject;
import com.tivo.kmttg.gui.TableMap;
import com.tivo.kmttg.gui.table.TableUtil;
import com.tivo.kmttg.gui.comparator.DateComparator;
import com.tivo.kmttg.gui.comparator.DurationComparator;
import com.tivo.kmttg.gui.comparator.StringChannelComparator;
import com.tivo.kmttg.gui.sortable.sortableDate;
import com.tivo.kmttg.gui.sortable.sortableDuration;
import com.tivo.kmttg.main.auto;
import com.tivo.kmttg.main.config;
import com.tivo.kmttg.rpc.Remote;
import com.tivo.kmttg.rpc.rnpl;
import com.tivo.kmttg.util.debug;
import com.tivo.kmttg.util.log;

public class todoTable extends TableMap {
   private String[] TITLE_cols = {"DATE", "SHOW", "CHANNEL", "DUR"};
   public TableView<Tabentry> TABLE = null;
   public Hashtable<String,JSONArray> tivo_data = new Hashtable<String,JSONArray>();
   private String currentTivo = null;
   
   // TableMap overrides
   @Override
   public JSONObject getJson(int row) {
      return GetRowData(row);
   }
   @Override
   public int[] getSelected() {
      return TableUtil.GetSelectedRows(TABLE);
   }
   @Override
   public Boolean isRemote() {
      return true;
   }
   @Override
   public void clear() {
      TABLE.getItems().clear();
   }
   @Override
   public TableView<?> getTable() {
      return TABLE;
   }
   
   public todoTable() {
      TABLE = new TableView<Tabentry>();
      TABLE.getSelectionModel().setSelectionMode(SelectionMode.SINGLE); // Allow only single row selection
      TABLE.setRowFactory(new ColorRowFactory()); // For row background color handling
      // Special sort listener to set sort order to ascending date when no sort is selected
      TABLE.getSortOrder().addListener(new ListChangeListener<TableColumn<Tabentry, ?>>() {
         @Override
         public void onChanged(Change<? extends TableColumn<Tabentry, ?>> change) {
            change.next();
            if (change != null && change.toString().contains("removed")) {
               if (change.getRemoved().get(0).getText().equals("DATE"))
                  return;
               int date_col = TableUtil.getColumnIndex(TABLE, "DATE");
               TABLE.getSortOrder().setAll(Collections.singletonList(TABLE.getColumns().get(date_col)));
               TABLE.getColumns().get(date_col).setSortType(TableColumn.SortType.ASCENDING);
            }
         }
      });
      
      for (String colName : TITLE_cols) {
         if (colName.equals("DATE")) {
            TableColumn<Tabentry,sortableDate> col = new TableColumn<Tabentry,sortableDate>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,sortableDate>(colName));
            col.setComparator(new DateComparator()); // Custom column sort
            col.setStyle("-fx-alignment: CENTER-RIGHT;");
            TABLE.getColumns().add(col);
         } else if (colName.equals("DUR")) {
            TableColumn<Tabentry,sortableDuration> col = new TableColumn<Tabentry,sortableDuration>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,sortableDuration>(colName));
            col.setComparator(new DurationComparator()); // Custom column sort
            col.setStyle("-fx-alignment: CENTER;");
            TABLE.getColumns().add(col);
         } else {
            // Regular String sort
            TableColumn<Tabentry,String> col = new TableColumn<Tabentry,String>(colName);
            col.setCellValueFactory(new PropertyValueFactory<Tabentry,String>(colName));
            if (colName.equals("CHANNEL"))
               col.setComparator(new StringChannelComparator()); // Custom column sort
            TABLE.getColumns().add(col);
         }
      }
      
      // Add keyboard listener
      TABLE.setOnKeyPressed(new EventHandler<KeyEvent>() {
         public void handle(KeyEvent e) {
            KeyPressed(e);
         }
      });
      
      // Define selection listener to detect table row selection changes
      TABLE.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Tabentry>() {
         @Override
         public void changed(ObservableValue<? extends Tabentry> obs, Tabentry oldSelection, Tabentry newSelection) {
            if (newSelection != null) {
               TABLERowSelected(newSelection);
            }
         }
      });
                              
      // Add right mouse button handler
      TableUtil.AddRightMouseListener(TABLE);
      
   }

   // ColorRowFactory for setting row background color
   private class ColorRowFactory implements Callback<TableView<Tabentry>, TableRow<Tabentry>> {
      public TableRow<Tabentry> call(TableView<Tabentry> tableView) {
         TableRow<Tabentry> row = new TableRow<Tabentry>() {
            @Override
            public void updateItem(Tabentry entry, boolean empty) {
               super.updateItem(entry,  empty);
               styleProperty().unbind(); setStyle("");
               if (entry != null) {
                  JSONObject json = entry.getDATE().json;
                  if (json != null) {
                     try {
                        if (json.has("state")) {
                           if (json.getString("state").equals("inProgress"))
                              TableUtil.setRowColor(this, config.tableBkgndRecording);
                        }
                        
                        if (config.showHistoryInTable == 1 && json.has("partnerCollectionId")) {
                           if (auto.keywordMatchHistoryFast(json.getString("partnerCollectionId"), false))
                              TableUtil.setRowColor(this, config.tableBkgndInHistory);
                        }
                     } catch (JSONException e) {
                        log.error("todoTable ColorRowFactory - " + e.getMessage());
                     }
                  }
               }
            }
         };
         return row;
      }
   }   
   
   public static class Tabentry {
      public String title = "";
      public sortableDate date;
      public String channel = "";
      public sortableDuration duration;

      public Tabentry(JSONObject data) {
         try {
            String startString=null, endString=null;
            long start=0, end=0;
            if (data.has("scheduledStartTime")) {
               startString = data.getString("scheduledStartTime");
               start = TableUtil.getLongDateFromString(startString);
               endString = data.getString("scheduledEndTime");
               end = TableUtil.getLongDateFromString(endString);
            } else if (data.has("startTime")) {
               start = TableUtil.getStartTime(data);
               end = TableUtil.getEndTime(data);
            }
            title = TableUtil.makeShowTitle(data);
            channel = TableUtil.makeChannelName(data);
            
            date = new sortableDate(data, start);
            duration = new sortableDuration(end-start, false);
         } catch (Exception e) {
            log.error("todoTable Tabentry - " + e.getMessage());
         }
      }
      
      public sortableDate getDATE() {
         return date;
      }
      
      public String getSHOW() {
         return title;
      }

      public String getCHANNEL() {
         return channel;
      }

      public sortableDuration getDUR() {
         return duration;
      }
      
      public String toString() {
         return title;
      }
   }
      
   public JSONObject GetRowData(int row) {
      return TABLE.getItems().get(row).getDATE().json;
   }

    public void AddRows(String tivoName, JSONArray data) {
       try {
          for (int i=0; i<data.length(); ++i) {
             AddRow(data.getJSONObject(i));
          }
          tivo_data.put(tivoName, data);
          currentTivo = tivoName;
          TableUtil.autoSizeTableViewColumns(TABLE, true);
          if (config.gui.remote_gui != null) {
             config.gui.remote_gui.setTivoName("todo", tivoName);
             refreshNumber();
          }
       } catch (JSONException e) {
          log.error("todoTable AddRows - " + e.getMessage());
       }
    }
    
    private void AddRow(JSONObject data) {
       debug.print("data=" + data);
       TABLE.getItems().add(new Tabentry(data));
    }
    
    private void TABLERowSelected(Tabentry entry) {
       // Get column items for selected row 
       sortableDate s = entry.getDATE();
       if (s.folder) {
          // Folder entry - don't display anything
       } else {
          try {
             // Non folder entry so print single entry info
             sortableDuration dur = entry.getDUR();
             String message = TableUtil.makeShowSummary(s, dur);
             String title = "\nToDo: ";
             if (s.json.has("title"))
                title += s.json.getString("title");
             if (s.json.has("subtitle"))
                title += " - " + s.json.getString("subtitle");
             if (s.json.has("state") && s.json.getString("state").equals("inProgress"))
                title += " (currently recording)";
             log.warn(title);
             log.print(message);

             if (config.gui.show_details.isShowing())
                config.gui.show_details.update(TABLE, currentTivo, s.json);
          } catch (JSONException e) {
             log.error("TABLERowSelected - " + e.getMessage());
             return;
          }
       }
    }
    
    // Handle keyboard presses
    private void KeyPressed(KeyEvent e) {
       if (e.isControlDown())
          return;
       KeyCode keyCode = e.getCode();
       if (keyCode == KeyCode.DELETE){
          // Delete key has special action
          DeleteCB();
       }
       else if (keyCode == KeyCode.C) {
          config.gui.remote_gui.todo_tab.cancel.fire();
       }
       else if (keyCode == KeyCode.M) {
          config.gui.remote_gui.todo_tab.modify.fire();
       }
       else if (keyCode == KeyCode.I) {
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          JSONObject json = GetRowData(selected[0]);
          if (json != null) {
             config.gui.show_details.update(TABLE, currentTivo, json);
          }
       } else if (keyCode == KeyCode.J) {
          // Print json of selected row to log window
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          JSONObject json = GetRowData(selected[0]);
          if (json != null)
             rnpl.pprintJSON(json);
       } else if (keyCode == KeyCode.Q) {
          // Web query currently selected entry
          int[] selected = TableUtil.GetSelectedRows(TABLE);
          if (selected == null || selected.length < 1)
             return;
          JSONObject json = GetRowData(selected[0]);
          if (json != null && json.has("title")) {
             try {
                String title = json.getString("title");
                if (json.has("subtitle"))
                   title = title + " - " + json.getString("subtitle");
                TableUtil.webQuery(title);
             } catch (JSONException e1) {
                log.error("KeyPressed Q - " + e1.getMessage());
             }
          }
       }
    }
    
    public void DeleteCB() {
       int[] selected = TableUtil.GetSelectedRows(TABLE);
       if (selected == null || selected.length != 1) {
          log.error("Must select a single table row.");
          return;
       }
       if (currentTivo == null) {
          log.error("Table not initialized");
          return;
       }
       int row;
       String title;
       JSONObject json;
       Remote r = config.initRemote(currentTivo);
       if (r.success) {
          // NOTE: Intentionally only remove 1 row at a time because removing rows from table
          row = selected[0];
          json = GetRowData(row);
          if (json != null) {
             try {
                title = json.getString("title");
                if (json.has("subtitle"))
                   title += " - " + json.getString("subtitle");
                log.warn("Cancelling ToDo show on TiVo '" + currentTivo + "': " + title);
                JSONObject o = new JSONObject();
                JSONArray a = new JSONArray();
                a.put(json.getString("recordingId"));
                o.put("recordingId", a);
                if ( r.Command("Cancel", o) != null ) {
                   TABLE.getItems().remove(row);
                   tivo_data.get(currentTivo).remove(row);
                   refreshNumber();
                }
             } catch (JSONException e1) {
                log.error("ToDo cancel - " + e1.getMessage());
             }
          }
          r.disconnect();                   
       }
    }
    
    // Refresh the # SHOWS label in the ToDo tab
    private void refreshNumber() {
       config.gui.remote_gui.todo_tab.label.setText("" + tivo_data.get(currentTivo).length() + " SHOWS");
    }
    
    // Schedule a single recording
    public void recordSingle(String tivoName) {
       int[] selected = TableUtil.GetSelectedRows(TABLE);
       if (selected.length > 0) {
          int row;
          JSONArray entries = new JSONArray();
          JSONObject json;
          for (int i=0; i<selected.length; ++i) {
             row = selected[i];
             json = GetRowData(row);
             entries.put(json);
          }
          TableUtil.recordSingleCB(tivoName, entries);
       }
    }
}