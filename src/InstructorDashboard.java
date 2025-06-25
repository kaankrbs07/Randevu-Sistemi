import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import app.model.InstructorItem;

/**
 * Öğretim Üyesi Paneli: - Müsaitlik tanımlama (gün ve saat aralıkları) - Gelen
 * randevu taleplerini görüntüleme - Talepleri onaylama, reddetme veya yeniden
 * zaman önerme
 */
public class InstructorDashboard extends JFrame {
	private static final long serialVersionUID = 1L;
	private final int instructorId;
	private JTable availTbl, reqTbl;
	private DefaultTableModel availM, reqM;
	private JComboBox<DayOfWeek> dayCombo = new JComboBox<>(DayOfWeek.values());
	private JSpinner startSpin;
	private JSpinner endSpin;
	private String fullName;
	private JButton addAvailBtn = new JButton("Müsaitlik Ekle");
	private JButton removeAvailBtn = new JButton("Sil");
	private JButton approveBtn = new JButton("Onayla");
	private JButton rejectBtn = new JButton("Reddet");
	private JButton suggestBtn = new JButton("Zaman Öner");
	private InstructorItem instructorItem;

	public InstructorDashboard(InstructorItem instructorItem) {
		this.instructorItem = instructorItem;
		this.instructorId = instructorItem.getInstructorId();
		this.fullName = instructorItem.getName();

		setTitle(fullName + " | Öğretim Üyesi Paneli");
		setSize(900, 600);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setLayout(new BorderLayout(10, 10));

		// Spinners as time editors
		startSpin = new JSpinner(new SpinnerDateModel());
		JSpinner.DateEditor startEditor = new JSpinner.DateEditor(startSpin, "HH:mm");
		startSpin.setEditor(startEditor);

		endSpin = new JSpinner(new SpinnerDateModel());
		JSpinner.DateEditor endEditor = new JSpinner.DateEditor(endSpin, "HH:mm");
		endSpin.setEditor(endEditor);

		// North: Availability controls
		JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
		north.add(new JLabel("Gün:"));
		north.add(dayCombo);
		north.add(new JLabel("Başlangıç:"));
		north.add(startSpin);
		north.add(new JLabel("Bitiş:"));
		north.add(endSpin);
		north.add(addAvailBtn);
		north.add(removeAvailBtn);
		add(north, BorderLayout.NORTH);

		// Center: Split Pane with two tables
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		split.setResizeWeight(0.4);

		availM = new DefaultTableModel(new String[] { "ID", "Gün", "Başlangıç", "Bitiş" }, 0) {

			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int r, int c) {
				return false;
			}
		};
		availTbl = new JTable(availM);
		split.setTopComponent(new JScrollPane(availTbl));

		reqM = new DefaultTableModel(new String[] { "ID", "Öğrenci", "Tarih", "Saat", "Durum" }, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int r, int c) {
				return false;
			}
		};
		reqTbl = new JTable(reqM);
		split.setBottomComponent(new JScrollPane(reqTbl));

		add(split, BorderLayout.CENTER);

		// South: Request action buttons
		JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		south.add(approveBtn);
		south.add(rejectBtn);
		south.add(suggestBtn);
		add(south, BorderLayout.SOUTH);

		// Action listeners
		addAvailBtn.addActionListener(e -> addAvailability());
		removeAvailBtn.addActionListener(e -> removeAvailability());
		approveBtn.addActionListener(e -> updateRequestStatus("APPROVED", null));
		rejectBtn.addActionListener(e -> updateRequestStatus("REJECTED", null));
		suggestBtn.addActionListener(e -> {
			String suggestion = JOptionPane.showInputDialog(this, "Yeni zaman dilimini girin (HH:mm - HH:mm):",
					"Zaman Öner", JOptionPane.PLAIN_MESSAGE);
			if (suggestion != null && !suggestion.trim().isEmpty()) {
				updateRequestStatus("SUGGESTED", suggestion.trim());
			}
		});

		// Load initial data
		loadAvailabilities();
		loadRequests();

		setVisible(true);
	}
     
	// Öğretim üyesine ait tanımlı tüm müsaitlik saatlerini tabloya (availTbl) yükler
	private void loadAvailabilities() {
		// Tabloyu temizle
		availM.setRowCount(0);

		String sql = """
				SELECT Id,
				       Day_Of_Week,
				       Start_Time,
				       End_Time
				FROM dbo.Availabilities
				WHERE Instructor_Id = ?
				""";

		try (Connection c = DatabaseConnector.connect(); PreparedStatement ps = c.prepareStatement(sql)) {

			// Tek parametreyi buraya bağlıyoruz
			ps.setInt(1, instructorId);

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					// Modelde 4 sütun tanımlı: ID, Gün, Başlangıç, Bitiş
					availM.addRow(new Object[] { rs.getInt("Id"), rs.getString("Day_Of_Week"),
							rs.getTime("Start_Time").toLocalTime().toString(),
							rs.getTime("End_Time").toLocalTime().toString() });
				}
			}

		} catch (SQLException ex) {
			showError("Müsaitlikler yüklenirken", ex);
		}
	}

	// Öğretim üyesine ait bekleyen randevu taleplerini (Status = 'PENDING') tabloya
	// yükleyen metot
	private void loadRequests() {
		// Tabloyu temizle (önceki satırları sil)
		reqM.setRowCount(0);
		// Appointments tablosundan randevular alınır,
		// öğrenci bilgisi için Users tablosu ile join yapılır,
		// sadece belirtilen öğretim üyesine (Instructor_Id) ve bekleyen (PENDING)
		// durumdakiler alınır
		String sql = "SELECT a.Id, u.FullName, a.Appointment_Date, a.Time_Slot, a.Status " + "FROM dbo.Appointments a "
				+ "JOIN dbo.Users u ON a.Student_Id = u.Id " + "WHERE a.Instructor_Id = ? AND a.Status = 'PENDING'";

		try (Connection c = DatabaseConnector.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setInt(1, instructorId); // instructorId doğru set ediliyor mu kontrol et

			try (ResultSet rs = ps.executeQuery()) {
				DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
				DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");

				while (rs.next()) {
					int id = rs.getInt("Id");
					String studentName = rs.getString("FullName");
					String date = rs.getDate("Appointment_Date").toLocalDate().format(df);
					String time = rs.getTime("Time_Slot").toLocalTime().format(tf); // 🔧 Saat biçimleme
					String status = rs.getString("Status");
					// Konsola log yazdırılır (debug için)
					System.out.println(">> Satır eklendi: " + rs.getInt("Id"));
					// Tablo modeline yeni satır eklenir
					reqM.addRow(new Object[] { id, studentName, date, time, status });
				}
			}
		} catch (SQLException ex) {
			showError("Talepler yüklenirken", ex);
		}
		// Tablo görünümü yenilenir
		reqTbl.repaint();
	}

	// Öğretim üyesinin yeni bir müsaitlik saati eklemesini sağlayan metot
	private void addAvailability() {
		// ComboBox'tan seçilen gün alınır
		DayOfWeek dow = (DayOfWeek) dayCombo.getSelectedItem();
		// Başlangıç saati spinner'dan alınır ve "HH:mm" formatında LocalTime'a çevrilir
		LocalTime start = LocalTime.parse(new java.text.SimpleDateFormat("HH:mm").format(startSpin.getValue()));
		// Bitiş saati spinner'dan alınır ve "HH:mm" formatında LocalTime'a çevrilir
		LocalTime end = LocalTime.parse(new java.text.SimpleDateFormat("HH:mm").format(endSpin.getValue()));

		String sql = "INSERT INTO dbo.Availabilities "
				+ "(Instructor_Id, Day_Of_Week, Start_Time, End_Time) VALUES (?,?,?,?)";
		try (Connection c = DatabaseConnector.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setInt(1, instructorItem.getInstructorId());
			ps.setString(2, dow.name());
			ps.setTime(3, Time.valueOf(start));
			ps.setTime(4, Time.valueOf(end));
			ps.executeUpdate();
			// Tabloyu yenile (yeni müsaitlik ekrandaki tabloya yansısın)
			loadAvailabilities();
		} catch (SQLException ex) {
			showError("Müsaitlik eklenirken", ex);
		}
	}

	// Öğretim üyesinin seçtiği müsaitlik saatini veritabanından siler
	private void removeAvailability() {
		int row = availTbl.getSelectedRow();
		if (row < 0)
			return;
		int id = (int) availM.getValueAt(row, 0);
		String sql = "DELETE FROM dbo.Availabilities WHERE Id = ?";
		try (Connection c = DatabaseConnector.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setInt(1, id);
			ps.executeUpdate();
			// Güncel durumu yansıtmak için tablo yeniden yüklenir
			loadAvailabilities();
		} catch (SQLException ex) {
			showError("Müsaitlik silinirken", ex);
		}
	}

	// Seçili randevu isteğinin durumunu ve istenirse saatini günceller
	private void updateRequestStatus(String status, String newSlot) {
		int row = reqTbl.getSelectedRow();
		if (row < 0)
			return;
		int id = (int) reqM.getValueAt(row, 0);
		// SQL sorgusu: Status güncellenir, eğer newSlot null değilse Time_Slot da
		// değiştirilir
		// COALESCE(? , Time_Slot) → eğer newSlot null değilse onu, null ise mevcut
		// değeri kullan
		String sql = "UPDATE dbo.Appointments SET Status = ?, Time_Slot = COALESCE(?, Time_Slot) WHERE Id = ?";
		try (Connection c = DatabaseConnector.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, status);
			ps.setString(2, newSlot);
			ps.setInt(3, id);
			ps.executeUpdate();
			loadRequests();
		} catch (SQLException ex) {
			showError("Talep güncellenirken", ex);
		}
	}

	// Hata gösterme fonksiyonu
	private void showError(String msg, Exception ex) {
		ex.printStackTrace();
		JOptionPane.showMessageDialog(this, msg + ":\n" + ex.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
	}
}
