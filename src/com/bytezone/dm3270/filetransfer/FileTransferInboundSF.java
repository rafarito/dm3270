package com.bytezone.dm3270.filetransfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileTransferInboundSF extends FileTransferSF
{
  private static final Logger logger = LoggerFactory.getLogger (FileTransferInboundSF.class);

  public FileTransferInboundSF (byte[] buffer, int offset, int length)
  {
    super (buffer, offset, length, "Inbound");

    TransferRecord transferRecord;

    int ptr = 3;
    while (ptr < data.length)
    {
      switch (data[ptr])
      {
        case 0x63:
          transferRecord = new RecordNumber (data, ptr);
          break;

        case 0x69:
          transferRecord = new ErrorRecord (data, ptr);
          break;

        case (byte) 0xC0:
          transferRecord = new DataRecord (data, ptr);
          break;

        default:
          logger.warn ("Unknown inbound TransferRecord: {}", String.format ("%02X", data[ptr]));
          transferRecord = new TransferRecord (data, ptr);
      }
      transferRecords.add (transferRecord);
      ptr += transferRecord.length ();
    }

    if (debug)
    {
      logger.debug ("{}", this);
      logger.debug ("-----------------------------------------"
          + "------------------------------");
    }
  }
}