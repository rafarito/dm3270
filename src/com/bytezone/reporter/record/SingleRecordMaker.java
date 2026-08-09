package com.bytezone.reporter.record;

import java.util.ArrayList;
import java.util.List;

// -----------------------------------------------------------------------------------//
public class SingleRecordMaker extends DefaultRecordMaker
// -----------------------------------------------------------------------------------//
{
  // ---------------------------------------------------------------------------------//
  public SingleRecordMaker ()
  // ---------------------------------------------------------------------------------//
  {
    super ("One record");
    weight = .1;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  protected List<Record> split (int length)
  // ---------------------------------------------------------------------------------//
  {
    List<Record> records = new ArrayList<Record> ();
    records.add (new Record (buffer, 0, length, 0));
    return records;
  }

  // ---------------------------------------------------------------------------------//
  @Override
  protected byte[] join (List<Record> records)
  // ---------------------------------------------------------------------------------//
  {
    int bufferLength = 0;
    for (Record record : records)
      bufferLength += record.length;

    byte[] buffer = new byte[bufferLength];

    int ptr = 0;
    for (Record record : records)
    {
      System.arraycopy (record.buffer, record.offset, buffer, ptr, record.length);
      ptr += record.length;
    }

    return buffer;
  }
}