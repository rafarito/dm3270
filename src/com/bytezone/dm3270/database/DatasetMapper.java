package com.bytezone.dm3270.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/*
 * A traducao entre um Dataset e uma linha da tabela DATASETS.
 *
 * Estava dentro do DatabaseThread, em dois metodos privados que liam os campos do Dataset
 * direto - sem passar por metodo nenhum, porque os dois tipos eram vizinhos de pacote. Era o
 * unico motivo pelo qual Dataset nao podia sair daqui, e por tabela o unico motivo pelo qual
 * o pacote display, que so usa os setters, continuava preso a persistencia.
 *
 * Aqui e o lugar: quem conhece o esquema SQL e quem sabe traduzir para ele. O Dataset passa
 * a ser lido por acessores, e a ordem dos quinze parametros - que e o que um erro de indice
 * quebraria em silencio - esta coberta por DatabaseTest.datasetColumnsAreBoundInOrder, que
 * le as colunas do arquivo SQLite pelo nome.
 *
 * A ordem dos parametros e das colunas lidas e exatamente a de antes.
 */
// -----------------------------------------------------------------------------------//
final class DatasetMapper
// -----------------------------------------------------------------------------------//
{
  private DatasetMapper ()
  {
  }

  // ---------------------------------------------------------------------------------//
  static void bind (PreparedStatement ps, Dataset dataset) throws SQLException
  // ---------------------------------------------------------------------------------//
  {
    ps.setString (1, dataset.getVolume ());
    ps.setString (2, dataset.getDevice ());
    ps.setString (3, dataset.getCatalog ());
    ps.setInt (4, dataset.getTracks ());
    ps.setInt (5, dataset.getCylinders ());
    ps.setInt (6, dataset.getPercent ());
    ps.setInt (7, dataset.getExtents ());
    ps.setString (8, dataset.getDsorg ());
    ps.setString (9, dataset.getRecfm ());
    ps.setInt (10, dataset.getLrecl ());
    ps.setInt (11, dataset.getBlksize ());
    ps.setDate (12, dataset.getCreatedSQL () == null ? null : dataset.getCreatedSQL ());
    ps.setDate (13, dataset.getExpiresSQL () == null ? null : dataset.getExpiresSQL ());
    ps.setDate (14, dataset.getReferredSQL () == null ? null : dataset.getReferredSQL ());
    ps.setString (15, dataset.getName ());
  }

  // ---------------------------------------------------------------------------------//
  static Dataset read (ResultSet rs) throws SQLException
  // ---------------------------------------------------------------------------------//
  {
    Dataset dataset = new Dataset (rs.getString ("name"));

    dataset.setSpace (rs.getInt ("tracks"), rs.getInt ("cylinders"),
        rs.getInt ("extents"), rs.getInt ("percent"));
    dataset.setDisposition (rs.getString ("dsorg"), rs.getString ("recfm"),
        rs.getInt ("lrecl"), rs.getInt ("blksize"));
    dataset.setLocation (rs.getString ("volume"), rs.getString ("device"),
        rs.getString ("catalog"));
    dataset.setDates (rs.getDate ("created"), rs.getDate ("expires"),
        rs.getDate ("referred"));

    return dataset;
  }
}
