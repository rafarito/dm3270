package com.bytezone.dm3270.filetransfer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.watch.ScreenWatcher;
import com.bytezone.dm3270.utilities.FileSaver;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;

public class TransferDialog
{
  private static final Logger logger = LoggerFactory.getLogger (TransferDialog.class);

  protected static final DateFormat df = new SimpleDateFormat ("yyyy/MM/dd HH:mm:ss");
  protected static final Pattern jclPattern =
      Pattern.compile (".*\\.(CNTL|JCL)[.(].*\\)");
  protected static final Pattern procPattern =
      Pattern.compile (".*\\.(PROC|PARM)LIB[.(].*\\)");

  protected final Dialog<IndFileCommand> dialog = new Dialog<> ();

  protected final GridPane grid = new GridPane ();
  protected final Font labelFont = Font.font ("Monospaced", 14);
  protected final ComboBox<String> datasetComboBox = new ComboBox<> ();
  protected final CheckBox asciiCrlfCheckBox = new CheckBox ("ASCII CRLF");

  protected final ButtonType btnTypeOK = new ButtonType ("OK", ButtonData.OK_DONE);
  protected final ButtonType btnTypeCancel =
      new ButtonType ("Cancel", ButtonData.CANCEL_CLOSE);

  protected final ScreenWatcher screenWatcher;
  protected final Path homePath;
  protected final int baseLength;
  protected final String commandDirection;
  protected File selectedLocalFile;

  public TransferDialog (ScreenWatcher screenWatcher, Path homePath, int baseLength,
      String title, String commandDirection)
  {
    this.screenWatcher = screenWatcher;
    this.homePath = homePath;
    this.baseLength = baseLength;
    this.commandDirection = commandDirection;

    grid.setPadding (new Insets (10, 35, 10, 20));
    grid.setHgap (10);
    grid.setVgap (10);

    dialog.setTitle (title);
    dialog.getDialogPane ().getButtonTypes ().addAll (btnTypeOK, btnTypeCancel);
    dialog.getDialogPane ().setContent (grid);

    dialog.setResultConverter (btnType ->
    {
      if (btnType != btnTypeOK)
        return null;

      String datasetName = getSelectedDatasetName ();
      if (datasetName.isEmpty ())
        return null;

      String commandText = getCommandText (commandDirection, datasetName);
      if (commandText.isEmpty ())
        return null;

      IndFileCommand indFileCommand = new IndFileCommand (commandText);

      if (selectedLocalFile != null)
        indFileCommand.setLocalFile (selectedLocalFile);
      else
      {
        String saveFolderName = FileSaver.getSaveFolderName (homePath, datasetName);
        Path saveFile = Paths.get (saveFolderName, datasetName);
        indFileCommand.setLocalFile (saveFile.toFile ());
      }

      return indFileCommand;
    });

    datasetComboBox.setEditable (true);
    datasetComboBox.setPrefWidth (280);
    datasetComboBox.setStyle ("-fx-font-size: 13; -fx-font-family: Monospaced");

    asciiCrlfCheckBox.setSelected (true);
    asciiCrlfCheckBox.setFont (labelFont);

    buildDatasetList ();
  }

  public Optional<IndFileCommand> showAndWait ()
  {
    buildDatasetList ();
    return dialog.showAndWait ();
  }

  private void buildDatasetList ()
  {
    String currentText = getSelectedDatasetName ();
    datasetComboBox
        .setItems (FXCollections.observableList (screenWatcher.getRecentDatasets ()));
    String single = screenWatcher.getSingleDataset ();
    if (single != null && !single.isEmpty ())
    {
      datasetComboBox.getSelectionModel ().select (single);
      datasetComboBox.getEditor ().setText (single);
    }
    else if (!currentText.isEmpty ())
    {
      datasetComboBox.getEditor ().setText (currentText);
    }
  }

  protected String getSelectedDatasetName ()
  {
    String text = datasetComboBox.getEditor ().getText ();
    if (text == null || text.trim ().isEmpty ())
      text = datasetComboBox.getSelectionModel ().getSelectedItem ();
    if (text == null)
      return "";

    text = text.trim ().toUpperCase ();
    if (text.length () >= 2 && text.startsWith ("'") && text.endsWith ("'"))
      text = text.substring (1, text.length () - 1).trim ();
    return text;
  }

  protected String getCommandText (String direction, String datasetName)
  {
    boolean useCrlf = asciiCrlfCheckBox.isSelected ();

    // remove prefix to save space on the command line
    String prefix = screenWatcher.getPrefix ();
    if (!prefix.isEmpty () && datasetName.startsWith (prefix))
    {
      if (datasetName.length () == prefix.length ())
      {
        logger.info ("Dataset name matches prefix - do not transfer");
        return "";
      }
      datasetName = datasetName.substring (prefix.length () + 1);
    }
    else
      datasetName = "'" + datasetName + "'";

    String tsoPrefix = screenWatcher.isTSOCommandScreen () ? "" : "TSO ";
    String options = useCrlf ? " ASCII CRLF" : "";

    return String.format ("%sIND$FILE %s %s%s", tsoPrefix, direction, datasetName,
                          options);
  }

  protected String formatDate (DatasetSummary dataset)
  {
    String date = dataset.getReferredDate ();
    if (date == null || date.isEmpty ())
      return "";
    else
    {
      String reformattedDate =
          date.substring (0, 4) + "/" + date.substring (5, 7) + "/" + date.substring (8);
      return reformattedDate + " " + dataset.getReferredTime ();
    }
  }

  protected String formatDate (Path saveFile)
  {
    try
    {
      BasicFileAttributes attr =
          Files.readAttributes (saveFile, BasicFileAttributes.class);
      return df.format (attr.lastModifiedTime ().toMillis ());
    }
    catch (IOException e)
    {
      return "IOException";
    }
  }
}