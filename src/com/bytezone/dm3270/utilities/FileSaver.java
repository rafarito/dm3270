package com.bytezone.dm3270.utilities;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// -----------------------------------------------------------------------------------//
public class FileSaver
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  public static Path getHomePath (String siteFolderName)
  // ---------------------------------------------------------------------------------//
  {
    String userHome = System.getProperty ("user.home");
    return Paths.get (userHome, "dm3270", "files", siteFolderName);
  }

  // ---------------------------------------------------------------------------------//
  public static Path getHomePath (Site site)
  // ---------------------------------------------------------------------------------//
  {
    if (site == null)
    {
      System.out.println ("Site is null");
      return null;
    }
    String userHome = System.getProperty ("user.home");
    return Paths.get (userHome, "dm3270", "files", site.getFolder ());
  }

  // ---------------------------------------------------------------------------------//
  public static String[] getSegments (String datasetName)
  // ---------------------------------------------------------------------------------//
  {
    // convert the dataset name into a potential path of folder names
    String[] segments = datasetName.split ("\\.");      // split into segments
    int last = segments.length - 1;

    // if the last segment contains a pds member name, remove it
    if (last >= 0 && segments[last].endsWith (")"))
    {
      int pos = segments[last].indexOf ('(');
      segments[last] = segments[last].substring (0, pos);
    }

    return segments;
  }

  // Determine the path of the folder in which the dataset should be stored
  // ---------------------------------------------------------------------------------//
  public static String getSaveFolderName (Path homePath, String datasetName)
  // ---------------------------------------------------------------------------------//
  {
    //    System.out.println ("Dataset name: " + datasetName);
    String[] segments = getSegments (datasetName);
    //    System.out.println ("Segments:");
    //    for (String segment : segments)
    //      System.out.println ("==>" + segment);

    int nextSegment = 0;
    String buildPath = homePath.toString ();

    while (nextSegment < segments.length)
    {
      Path nextPath = Paths.get (buildPath, segments[nextSegment++]);
      //      System.out.println ("checking: " + nextPath);
      if (Files.notExists (nextPath) || !Files.isDirectory (nextPath))
      {
        //        System.out.println ("Best path is: " + buildPath);
        return buildPath;
      }

      buildPath = nextPath.toString ();

      Path filePath = Paths.get (buildPath, datasetName);
      if (Files.exists (filePath))
      {
        //        System.out.println ("File exists at: " + buildPath);
        return buildPath;
      }
    }
    //    System.out.println ("Not found, using: " + buildPath);
    return buildPath;
  }
}
