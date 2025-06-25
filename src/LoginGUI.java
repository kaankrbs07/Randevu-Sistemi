import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import app.model.InstructorItem;

public class LoginGUI extends JFrame {
	private static final long serialVersionUID = 1L;
	private JTextField usernameField;
	private JPasswordField passwordField;

	public LoginGUI() {
		setTitle("Akademik Randevu Giriş");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(600, 600);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout());

		// Arka plan paneli
		JPanel background = new JPanel() {
			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				Graphics2D g2d = (Graphics2D) g;
				GradientPaint gp = new GradientPaint(0, 0, new Color(0, 123, 255), getWidth(), getHeight(),
						new Color(102, 16, 242));
				g2d.setPaint(gp);
				g2d.fillRect(0, 0, getWidth(), getHeight());
			}
		};
		background.setLayout(new GridBagLayout());

		// Login kartı
		JPanel loginCard = new JPanel();
		loginCard.setPreferredSize(new Dimension(360, 400));
		loginCard.setBackground(Color.WHITE);
		loginCard.setBorder(
				BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(150, 150, 150), 1),
						BorderFactory.createEmptyBorder(30, 30, 30, 30)));
		loginCard.setLayout(new BoxLayout(loginCard, BoxLayout.Y_AXIS));

		JLabel title = new JLabel("Giriş Paneli", SwingConstants.CENTER);
		title.setFont(new Font("Segoe UI", Font.BOLD, 24));
		title.setAlignmentX(Component.CENTER_ALIGNMENT);
		title.setBorder(BorderFactory.createEmptyBorder(10, 0, 20, 0));
		loginCard.add(title);

		usernameField = new JTextField();
		usernameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
		usernameField.setBorder(BorderFactory.createTitledBorder("Kullanıcı Adı"));
		usernameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
		loginCard.add(usernameField);

		passwordField = new JPasswordField();
		passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
		passwordField.setBorder(BorderFactory.createTitledBorder("Şifre"));
		passwordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
		loginCard.add(passwordField);

		JButton loginButton = new JButton("Giriş Yap");
		loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		loginButton.setPreferredSize(new Dimension(100, 40));
		loginButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
		loginButton.setBackground(new Color(102, 16, 242));
		loginButton.setForeground(Color.WHITE);
		loginButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
		loginButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		loginButton.setFocusPainted(false);
		loginButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
		loginButton.addActionListener(e -> login());
		loginCard.add(Box.createRigidArea(new Dimension(0, 20)));
		loginCard.add(loginButton);

		background.add(loginCard, new GridBagConstraints());
		add(background, BorderLayout.CENTER);
		setVisible(true);

		JButton registerBtn = new JButton("Kayıt Ol");
		registerBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		registerBtn.setPreferredSize(new Dimension(100, 40));
		registerBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
		registerBtn.setBackground(new Color(0, 123, 255));
		registerBtn.setForeground(Color.WHITE);
		registerBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
		registerBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		registerBtn.setFocusPainted(false);
		registerBtn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
		registerBtn.addActionListener(e -> SwingUtilities.invokeLater(RegisterGUI::new));

		loginCard.add(Box.createRigidArea(new Dimension(0, 10))); // Biraz boşluk ekleyin
		loginCard.add(registerBtn);
	}

	private void login() {
		String username = usernameField.getText();
		String password = new String(passwordField.getPassword());

		try (Connection conn = DatabaseConnector.connect()) {
			String sql = "SELECT * FROM Users WHERE Username = ? AND PasswordHash = ?";
			PreparedStatement stmt = conn.prepareStatement(sql);
			stmt.setString(1, username);
			stmt.setString(2, password);

			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				String role = rs.getString("Role");
				int userId = rs.getInt("Id");
				String fullName = rs.getString("FullName");

				JOptionPane.showMessageDialog(this, "Giriş başarılı! Hoş geldiniz, " + fullName);

				if (role.equals("Student")) {
					new StudentDashboard(userId, fullName);
				} else {
					// InstructorId'yi çek
					int instructorId = getInstructorIdByUserId(userId);

					if (instructorId != -1) {
						InstructorItem instructorItem = new InstructorItem(userId, instructorId, fullName);
						new InstructorDashboard(instructorItem);
					} else {
						JOptionPane.showMessageDialog(this, "Bu kullanıcıya ait öğretim üyesi bilgisi bulunamadı.");
					}
				}

				dispose();
			} else {
				JOptionPane.showMessageDialog(this, "Hatalı kullanıcı adı veya şifre.");
			}

		} catch (SQLException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Veritabanı bağlantı hatası.");
		}
	}

	// Verilen UserId'ye karşılık gelen Instructor (öğretim üyesi) ID'sini döndürür.
	private int getInstructorIdByUserId(int userId) {
		try (Connection conn = DatabaseConnector.connect();
				PreparedStatement ps = conn.prepareStatement("SELECT Id FROM Instructors WHERE UserId = ?")) {
			ps.setInt(1, userId);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getInt("Id");
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return -1; // Bulunamazsa
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(LoginGUI::new);
	}
}
