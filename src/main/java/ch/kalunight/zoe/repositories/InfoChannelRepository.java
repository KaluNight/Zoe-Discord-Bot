package ch.kalunight.zoe.repositories;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.annotation.Nullable;
import ch.kalunight.zoe.model.dto.DTO;

public class InfoChannelRepository {

  private static final String SELECT_INFOCHANNEL_WITH_GUILD_ID = "SELECT " + 
      "info_channel.infochannel_id, " + 
      "info_channel.infochannel_fk_server, " + 
      "info_channel.infochannel_channelid " + 
      "FROM info_channel " + 
      "INNER JOIN server ON info_channel.infochannel_fk_server = server.serv_id " +
      "WHERE serv_guildId = %d";
  
  private static final String INSERT_INTO_INFOCHANNEL = "INSERT INTO info_channel (infochannel_fk_server, infochannel_channelid) " +
      "VALUES (%d, %d)";
  
  private static final String INSERT_INTO_INFO_PANEL_MESSAGE = "INSERT INTO info_panel_message " +
      "(infopanel_fk_infochannel, infopanel_messageid) VALUES (%d, %d)";
  
  private static final String UPDATE_INFOCHANNEL_WITH_GUILD_ID =
      "UPDATE info_channel " +
      "SET infochannel_channelid = %d " +
      "FROM server " +
      "WHERE server.serv_guildId = %d AND " +
      "server.serv_id = info_channel.infochannel_fk_server";
  
  private static final String DELETE_INFOCHANNEL_WITH_GUILD_ID = "DELETE FROM info_channel " +
      "USING server " +
      "WHERE server.serv_id = info_channel.infochannel_fk_server AND server.serv_guildid = %d";
  
  private InfoChannelRepository() {
    //Hide default public constructor
  }
  
  @Nullable
  public static DTO.InfoChannel getInfoChannel(long guildId) throws SQLException{
    ResultSet result = null;
    try (Connection conn = RepoRessources.getConnection();
        Statement query = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);) {
      
      String finalQuery = String.format(SELECT_INFOCHANNEL_WITH_GUILD_ID, guildId);
      result = query.executeQuery(finalQuery);
      int rowCount = result.last() ? result.getRow() : 0;
      if(rowCount == 0) {
        return null;
      }
      return new DTO.InfoChannel(result);
    }finally {
      RepoRessources.closeResultSet(result);
    }
  }
  
  public static void createInfoChannel(long servId, long channelId) throws SQLException {
    try (Connection conn = RepoRessources.getConnection();
        Statement query = conn.createStatement();) {
      
      String finalQuery = String.format(INSERT_INTO_INFOCHANNEL, servId, channelId);
      query.execute(finalQuery);
    }
  }
  
  public static void createInfoPanelMessage(long infoChannelId, long messageId) throws SQLException {
    try (Connection conn = RepoRessources.getConnection();
        Statement query = conn.createStatement();) {
      
      String finalQuery = String.format(INSERT_INTO_INFO_PANEL_MESSAGE, infoChannelId, messageId);
      query.execute(finalQuery);
    }
  }
  
  public static void updateInfoChannel(long guildId, long channelId) throws SQLException {
    try (Connection conn = RepoRessources.getConnection();
        Statement query = conn.createStatement();) {
      
      String finalQuery = String.format(UPDATE_INFOCHANNEL_WITH_GUILD_ID, channelId, guildId);
      query.executeUpdate(finalQuery);
    }
  }
  
  public static void deleteInfoChannel(long guildId) throws SQLException {
    try (Connection conn = RepoRessources.getConnection();
        Statement query = conn.createStatement();) {
      
      String finalQuery = String.format(DELETE_INFOCHANNEL_WITH_GUILD_ID, guildId);
      query.execute(finalQuery);
    }
  }
}
