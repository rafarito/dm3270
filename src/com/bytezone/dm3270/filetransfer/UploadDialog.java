package com.bytezone.dm3270.filetransfer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.watch.ScreenWatcher;
import com.bytezone.dm3270.utilities.FileSaver;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

class UploadDialog extends TransferDialog
{
  private static final Logger logger = LoggerFactory.getLogger (UploadDialog.class);

  private final Label labelFromFolder = new Label ();
  private final Label labelFileDate = new Label ();
  private final Label labelDatasetDate = new Label ();
  private final Button btnBrowse = new Button ("Browse...");
  private final Node okButton;
  private File browsedFile;

  UploadDialog (ScreenWatcher screenWatcher, Path homePath, int baseLength)
  {
    super (screenWatcher, homePath, baseLength, "Upload dataset", "PUT");

    labelFromFolder.setFont (labelFont);
    labelFileDate.setFont (labelFont);
    labelDatasetDate.setFont (labelFont);

    grid.add (new Label ("Dataset"), 1, 1);
    grid.add (datasetComboBox, 2, 1);

    HBox folderBox = new HBox (10, labelFromFolder, btnBrowse);
    grid.add (new Label ("Local file"), 1, 2);
    grid.add (folderBox, 2, 2);

    grid.add (new Label ("File date"), 1, 3);
    grid.add (labelFileDate, 2, 3);

    grid.add (new Label ("Dataset date"), 1, 4);
    grid.add (labelDatasetDate, 2, 4);

    grid.add (new Label ("Options"), 1, 5);
    grid.add (asciiCrlfCheckBox, 2, 5);

    okButton = dialog.getDialogPane ().lookupButton (btnTypeOK);
    okButton.setDisable (true);

    btnBrowse.setOnAction (event -> chooseFile ());

    refreshUpload ();
    datasetComboBox.setOnAction (event -> refreshUpload ());
    datasetComboBox.getEditor ().textProperty ()
        .addListener ( (obs, oldVal, newVal) -> refreshUpload ());
  }

  private void chooseFile ()
  {
    FileChooser fileChooser = new FileChooser ();
    fileChooser.setTitle ("Select file to upload");
    if (homePath != null && Files.isDirectory (homePath))
      fileChooser.setInitialDirectory (homePath.toFile ());

    File chosen = fileChooser.showOpenDialog (
        dialog.getDialogPane ().getScene () != null
            ? dialog.getDialogPane ().getScene ().getWindow () : null);
    if (chosen != null && chosen.isFile ())
    {
      browsedFile = chosen;
      refreshUpload ();
    }
  }

  private void refreshUpload ()
  {
    String datasetSelected = getSelectedDatasetName ();

    Path activeFile = null;
    if (browsedFile != null && browsedFile.isFile ())
      activeFile = browsedFile.toPath ();
    else if (!datasetSelected.isEmpty ())
    {
      String saveFolderName = FileSaver.getSaveFolderName (homePath, datasetSelected);
      Path defaultFile = Paths.get (saveFolderName, datasetSelected);
      if (Files.exists (defaultFile) && Files.isRegularFile (defaultFile))
        activeFile = defaultFile;
    }

    selectedLocalFile = activeFile != null ? activeFile.toFile () : null;

    if (activeFile != null)
    {
      labelFromFolder.setText (activeFile.toString ());
      labelFileDate.setText (formatDate (activeFile));
    }
    else if (!datasetSelected.isEmpty ())
    {
      String saveFolderName = FileSaver.getSaveFolderName (homePath, datasetSelected);
      labelFromFolder.setText (saveFolderName);
      labelFileDate.setText ("");
    }
    else
    {
      labelFromFolder.setText (homePath != null ? homePath.toString () : "");
      labelFileDate.setText ("");
    }

    if (!datasetSelected.isEmpty ())
    {
      Optional<DatasetSummary> dataset = screenWatcher.getDataset (datasetSelected);
      if (dataset.isPresent ())
      {
        String date = dataset.get ().getReferredDate ();
        if (date == null || date.isEmpty ())
          labelDatasetDate.setText ("");
        else
        {
          String reformattedDate = date.substring (0, 4) + "/" + date.substring (5, 7) + "/"
              + date.substring (8);
          labelDatasetDate
              .setText (reformattedDate + " " + dataset.get ().getReferredTime ());
        }
      }
      else
      {
        logger.info ("Dataset not found");
        labelDatasetDate.setText ("");
      }
    }
    else
      labelDatasetDate.setText ("");

    okButton.setDisable (datasetSelected.isEmpty () || activeFile == null);
  }
}