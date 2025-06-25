import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import app.model.InstructorItem;

/**
 * Öğrencinin mevcut randevularını listeleyen ve yeni randevu talebi başlatan
 * Öğrenci randevularını görebilir - Yeni randevu ekranını açabilir - Seçili
 * randevuyu iptal edebilir
 */
public class StudentDashboard extends JFrame {

	private static final long serialVersionUID = 1L;
	private final int studentId;
	private final String fullName;
	private JTable appointmentsTable;
	private DefaultTableModel tableModel;
	private JButton newAppointmentButton;
	private JButton cancelAppointmentButton;

	public StudentDashboard(int studentId, String fullName) {
		this.studentId = studentId;
		this.fullName = fullName;

		setTitle(fullName + " | Randevularım");
		setSize(800, 500);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setLayout(new BorderLayout(10, 10));

		// Üst panel: Yeni Randevu ve İptal Butonları
		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		newAppointmentButton = new JButton("Yeni Randevu");
		cancelAppointmentButton = new JButton("Randevuyu İptal Et");
		topPanel.add(newAppointmentButton);
		topPanel.add(cancelAppointmentButton);
		add(topPanel, BorderLayout.NORTH);

		// Tablo Modeli ve Tablo
		tableModel = new DefaultTableModel(new String[] { "ID", "Öğretim Üyesi", "Tarih", "Saat", "Durum" }, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		appointmentsTable = new JTable(tableModel);
		add(new JScrollPane(appointmentsTable), BorderLayout.CENTER);

		// Buton Aksiyonları
		newAppointmentButton.addActionListener(e -> openAppointmentScreen());
		cancelAppointmentButton.addActionListener(e -> cancelSelectedAppointment());

		// Randevuları Yükle
		loadAppointments();

		setVisible(true);
	}

	// Veritabanından öğrenciye ait randevuları çekip tabloya yükler
	public void loadAppointments() {
		tableModel.setRowCount(0);

		String sql = "SELECT a.Id, i.Name AS InstructorName, a.Appointment_Date, a.Time_Slot, a.Status "
				+ "FROM Appointments a " + "JOIN Instructors i ON a.Instructor_Id = i.Id " + "WHERE a.Student_Id = ?";

		try (Connection conn = DatabaseConnector.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {

			ps.setInt(1, studentId); // öğrenci ID'sini sorguya bağla

			try (ResultSet rs = ps.executeQuery()) {
				DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
				DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

				while (rs.next()) {
					int id = rs.getInt("Id");
					String instructorName = rs.getString("InstructorName");
					LocalDate date = rs.getDate("Appointment_Date").toLocalDate();

					// Artık veritabanı TIME tipinde, doğrudan alınıp biçimlenebilir
					LocalTime time = rs.getTime("Time_Slot").toLocalTime();
					String formattedTime = time.format(timeFmt);

					String status = rs.getString("Status");

					// JTable'a satır olarak ekle
					tableModel.addRow(new Object[] { id, instructorName, date.format(dateFmt), formattedTime, status });
				}
			}
		} catch (SQLException ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this, "Randevular yüklenirken veritabanı hatası:\n" + ex.getMessage(), "Hata",
					JOptionPane.ERROR_MESSAGE);
		}
	}

// Yeni randevu ekranını açmaya yarayan metot
	private void openAppointmentScreen() {
		new AppointmentScreen(studentId, this);
	}

// Seçili randevuyu iptal etmeye yarayan metot
	private void cancelSelectedAppointment() {
		// Öğrenci randevu listesinden bir satır seçmiş mi kontrol edilir
		int row = appointmentsTable.getSelectedRow();
		if (row == -1) {
			JOptionPane.showMessageDialog(this, "Lütfen iptal etmek için bir randevu seçin.", "Uyarı",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		int appointmentId = (int) tableModel.getValueAt(row, 0);
		int confirm = JOptionPane.showConfirmDialog(this, "Seçili randevuyu iptal etmek istediğinize emin misiniz?",
				"Onay", JOptionPane.YES_NO_OPTION);
		// Kullanıcı "Evet" demediyse işlem iptal edilir
		if (confirm != JOptionPane.YES_OPTION)
			return;

		String sql = "DELETE FROM dbo.Appointments WHERE Id = ?";
		try (Connection conn = DatabaseConnector.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, appointmentId);
			ps.executeUpdate();

			JOptionPane.showMessageDialog(this, "Randevu başarıyla iptal edildi.", "Başarılı",
					JOptionPane.INFORMATION_MESSAGE);
			loadAppointments();
		} catch (SQLException ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this, "İptal sırasında hata:\n" + ex.getMessage(), "Hata",
					JOptionPane.ERROR_MESSAGE);
		}
	}
}
