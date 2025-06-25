import com.toedter.calendar.JCalendar;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.sql.Date;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import app.model.InstructorItem;

public class AppointmentScreen extends JFrame {
	private static final long serialVersionUID = 1L;
	private final int studentId;
	private final StudentDashboard parent;

	private JComboBox<InstructorItem> instructorCombo;
	private JCalendar calendar;
	private JComboBox<String> timeSlotCombo;
	private JButton getSlotsButton, submitButton;
	private Map<String, Integer> instructorMap = new HashMap<>();

	public AppointmentScreen(int studentId, StudentDashboard parent) {
		this.studentId = studentId;
		this.parent = parent;

		setTitle("Yeni Randevu Al");
		setSize(600, 450);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout(10, 10));

		// --- Üst PANEL: Hoca + Takvim ---
		JPanel top = new JPanel(new GridLayout(1, 2, 10, 0));
		top.setBorder(new EmptyBorder(10, 10, 0, 10));

		// Öğretim Üyesi
		instructorCombo = new JComboBox<>();
		loadInstructors(); // DB’den Users tablosundan Role='Instructor' çeker
		top.add(labeled("Öğretim Üyesi:", instructorCombo));

		// Takvim
		calendar = new JCalendar();
		top.add(labeled("Tarih Seç:", calendar));

		add(top, BorderLayout.NORTH);

		// --- Orta PANEL: Slot + Buton ---
		JPanel mid = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 20));
		timeSlotCombo = new JComboBox<>();
		timeSlotCombo.setPreferredSize(new Dimension(200, 25));
		getSlotsButton = new JButton("Saatleri Getir");
		getSlotsButton.addActionListener(e -> loadSlots());

		mid.add(new JLabel("Uygun Saatler:"));
		mid.add(timeSlotCombo);
		mid.add(getSlotsButton);
		add(mid, BorderLayout.CENTER);

		// --- Alt PANEL: Randevu Oluştur ---
		JPanel bot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		bot.setBorder(new EmptyBorder(0, 10, 10, 10));
		submitButton = new JButton("Randevu Oluştur");
		submitButton.addActionListener(e -> submitAppointment());
		bot.add(submitButton);
		add(bot, BorderLayout.SOUTH);

		setVisible(true);
	}

	private JPanel labeled(String text, JComponent comp) {
		JPanel p = new JPanel(new BorderLayout(5, 5));
		p.add(new JLabel(text), BorderLayout.NORTH);
		p.add(comp, BorderLayout.CENTER);
		return p;
	}

	// Seçilen öğretim üyesi ve tarihe göre, 20 dakikalık uygun randevu saatlerini
	// ComboBox'a yükler
	private void loadSlots() {
		InstructorItem inst = (InstructorItem) instructorCombo.getSelectedItem();
		if (inst == null) {
			JOptionPane.showMessageDialog(this, "Önce bir öğretim üyesi seçin!");
			return;
		}

		// Takvim bileşeninden seçilen tarih alınır ve LocalDate'e çevrilir
		LocalDate date = calendar.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

		// Seçilen tarihin haftalık gün ismi alınır (örneğin: "MONDAY")
		String dayName = date.getDayOfWeek().toString();

		// Dolu randevu saatleri ve uygun slotları tutmak için listeler oluşturulur
		List<LocalTime> bookedTimes = new ArrayList<>();
		List<String> availableSlots = new ArrayList<>();

		try (Connection c = DatabaseConnector.connect()) {

			// Seçilen hocanın o güne ait müsaitlik saat aralıklarını veritabanından çek
			String availSQL = "SELECT Start_Time, End_Time FROM Availabilities WHERE Instructor_Id=? AND Day_Of_Week=?";
			try (PreparedStatement ps = c.prepareStatement(availSQL)) {
				ps.setInt(1, inst.getInstructorId());
				ps.setString(2, dayName);

				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						LocalTime start = rs.getTime("Start_Time").toLocalTime();
						LocalTime end = rs.getTime("End_Time").toLocalTime();

						// Aynı tarih ve hocanın dolu saatlerini çek
						String takenSQL = "SELECT Time_Slot FROM Appointments WHERE Instructor_Id=? AND Appointment_Date=? AND Status <> 'REJECTED'";
						try (PreparedStatement ps2 = c.prepareStatement(takenSQL)) {
							ps2.setInt(1, inst.getInstructorId());
							ps2.setDate(2, Date.valueOf(date));

							try (ResultSet rs2 = ps2.executeQuery()) {
								while (rs2.next()) {
									bookedTimes.add(rs2.getTime("Time_Slot").toLocalTime());
								}
							}
						}

						// Müsaitlik aralığından 20 dakikalık boş slotlar üret
						List<String> slots = AppointmentUtils.generateAvailableSlots(start, end, bookedTimes);
						// Elde edilen slotlar genel listeye eklenir
						availableSlots.addAll(slots);
					}
				}
			}

			// UI tarafındaki ComboBox temizlenir ve yeni slotlar yüklenir
			timeSlotCombo.removeAllItems();
			for (String slot : availableSlots) {
				timeSlotCombo.addItem(slot);
			}

			if (availableSlots.isEmpty()) {
				JOptionPane.showMessageDialog(this, "Bu tarih için boş slot kalmadı.", "Bilgi",
						JOptionPane.INFORMATION_MESSAGE);
			}

		} catch (SQLException ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this, "Slotlar yüklenirken hata:\n" + ex.getMessage(), "Hata",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// Öğrencinin seçtiği hoca ve saat bilgisiyle yeni bir randevu talebi oluşturur
	private void submitAppointment() {
		// ComboBox'tan seçilen öğretim üyesi ve saat dilimi alınır
		InstructorItem selected = (InstructorItem) instructorCombo.getSelectedItem();
		String slot = (String) timeSlotCombo.getSelectedItem();

		if (selected == null || slot == null) {
			JOptionPane.showMessageDialog(this, "Önce hoca ve saat seçimi yapmalısınız.", "Uyarı",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		int selectedUserId = selected.getUserId(); // ComboBox’tan seçilen hocanın Users.Id’si
		int instructorId = getInstructorIdByUserId(selectedUserId); // Bu fonksiyon Instructors.Id'yi döndürür

		if (instructorId == -1) {
			JOptionPane.showMessageDialog(this, "Seçilen hocaya ait sistem ID'si bulunamadı.", "Hata",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		LocalDate date = calendar.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

		String[] parts = slot.split(" - ");
		LocalTime selectedTime = LocalTime.parse(parts[0]);

		try (Connection c = DatabaseConnector.connect();
				PreparedStatement ps = c.prepareStatement(
						"INSERT INTO Appointments (Student_Id, Instructor_Id, Appointment_Date, Time_Slot, Status) "
								+ "VALUES (?, ?, ?, ?, 'PENDING')")) {

			ps.setInt(1, studentId); // Giriş yapan öğrenci
			ps.setInt(2, instructorId); // ComboBox’tan seçilen hocanın InstructorId
			ps.setDate(3, Date.valueOf(date));
			ps.setTime(4, Time.valueOf(selectedTime));

			ps.executeUpdate();

			JOptionPane.showMessageDialog(this, "Randevu talebiniz iletildi.", "Başarılı",
					JOptionPane.INFORMATION_MESSAGE);

			parent.loadAppointments();
			dispose();

		} catch (SQLException ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this, "Veritabanı hatası:\n" + ex.getMessage(), "Hata",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// Verilen kullanıcı ID'sine (userId) karşılık gelen öğretim üyesi ID'sini
	// (Instructor.Id) döndüren metot
	private int getInstructorIdByUserId(int userId) {
		try (Connection c = DatabaseConnector.connect();
				PreparedStatement ps = c.prepareStatement("SELECT Id FROM Instructors WHERE UserId = ?")) {
			ps.setInt(1, userId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getInt("Id");
				}
			}
		} catch (SQLException ex) {
			ex.printStackTrace();
		}
		return -1; // Bulunamazsa
	}

	// Veritabanından öğretim üyelerini çekip instructorCombo (ComboBox)'a yükleyen
	// metot
	private void loadInstructors() {
		String sql = "SELECT i.Id AS InstructorId, u.Id AS UserId, u.FullName "
				+ "FROM Instructors i JOIN Users u ON i.UserId = u.Id";

		try (Connection c = DatabaseConnector.connect();
				PreparedStatement ps = c.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {

			// Her satır için bir InstructorItem oluşturulup ComboBox'a eklenir
			while (rs.next()) {
				int userId = rs.getInt("UserId");
				int InstructorId = rs.getInt("InstructorId");
				String name = rs.getString("FullName");
				// InstructorItem nesnesi ComboBox'a eklenir (ID'leri ve isim içerir)
				instructorCombo.addItem(new InstructorItem(userId, InstructorId, name));
			}

		} catch (SQLException ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(this, "Eğitmenler yüklenemedi:\n" + ex.getMessage(), "Hata",
					JOptionPane.ERROR_MESSAGE);
		}
	}

}
