package com.bytezone.dm3270.filetransfer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.bytezone.dm3270.datasets.DatasetSummary;
import com.bytezone.dm3270.watch.ScreenWatcher;
import com.bytezone.dm3270.utilities.FileSaver;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;

class DownloadDialog extends TransferDialog
{
  private final Label labelToFolder = new Label ();
  private final Label labelAction = new Label ();
  private final Label labelFileDate = new Label ();
  private final Label labelDatasetDate = new Label ();
  private final Button btnBrowse = new Button ("Browse...");
  private final Node okButton;
  private File browsedFolder;

  DownloadDialog (ScreenWatcher screenWatcher, Path homePath, int baseLength)
  {
    super (screenWatcher, homePath, baseLength, "Download dataset", "GET");

    labelToFolder.setFont (labelFont);
    labelAction.setFont (labelFont);
    labelFileDate.setFont (labelFont);
    labelDatasetDate.setFont (labelFont);

    grid.add (new Label ("Dataset"), 1, 1);
    grid.add (datasetComboBox, 2, 1);

    HBox folderBox = new HBox (10, labelToFolder, btnBrowse);
    grid.add (new Label ("Folder"), 1, 2);
    grid.add (folderBox, 2, 2);

    grid.add (new Label ("Action"), 1, 3);
    grid.add (labelAction, 2, 3);

    grid.add (new Label ("File date"), 1, 4);
    grid.add (labelFileDate, 2, 4);

    grid.add (new Label ("Dataset date"), 1, 5);
    grid.add (labelDatasetDate, 2, 5);

    grid.add (new Label ("Options"), 1, 6);
    grid.add (asciiCrlfCheckBox, 2, 6);

    okButton = dialog.getDialogPane ().lookupButton (btnTypeOK);

    btnBrowse.setOnAction (event -> chooseFolder ());

    refreshDownload ();
    datasetComboBox.setOnAction (event -> refreshDownload ());
    datasetComboBox.getEditor ().textProperty ()
        .addListener ( (obs, oldVal, newVal) -> refreshDownload ());
  }

  private void chooseFolder ()
  {
    DirectoryChooser directoryChooser = new DirectoryChooser ();
    directoryChooser.setTitle ("Select destination folder");
    if (browsedFolder != null && browsedFolder.isDirectory ())
      directoryChooser.setInitialDirectory (browsedFolder);
    else if (homePath != null && Files.isDirectory (homePath))
      directoryChooser.setInitialDirectory (homePath.toFile ());

    File chosen = directoryChooser.showDialog (
        dialog.getDialogPane ().getScene () != null
            ? dialog.getDialogPane ().getScene ().getWindow () : null);
    if (chosen != null && chosen.isDirectory ())
    {
      browsedFolder = chosen;
      refreshDownload ();
    }
  }

  private void refreshDownload ()
  {
    String datasetSelected = getSelectedDatasetName ();

    String saveFolderName;
    if (browsedFolder != null && browsedFolder.isDirectory ())
      saveFolderName = browsedFolder.getAbsolutePath ();
    else if (!datasetSelected.isEmpty ())
      saveFolderName = FileSaver.getSaveFolderName (homePath, datasetSelected);
    else
      saveFolderName = homePath != null ? homePath.toString () : "";

    labelToFolder.setText (saveFolderName);

    if (datasetSelected.isEmpty ())
    {
      selectedLocalFile = null;
      labelAction.setText ("");
      labelFileDate.setText ("");
      labelDatasetDate.setText ("");
      okButton.setDisable (true);
      return;
    }

    okButton.setDisable (false);

    Path saveFile = Paths.get (saveFolderName, datasetSelected);
    selectedLocalFile = saveFile.toFile ();

    Optional<DatasetSummary> dataset = screenWatcher.getDataset (datasetSelected);
    if (dataset.isPresent ())
      labelDatasetDate.setText (formatDate (dataset.get ()));
    else
      labelDatasetDate.setText ("");

    if (Files.exists (saveFile) && Files.isRegularFile (saveFile))
    {
      labelFileDate.setText (formatDate (saveFile));
      labelAction.setText ("Overwrite existing file");
    }
    else
    {
      labelFileDate.setText ("");
      labelAction.setText ("Create new file");
    }
  }
}