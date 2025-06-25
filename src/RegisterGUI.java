import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import app.model.InstructorItem;

/**
 * Kullanıcı kayıt penceresi. Veritabanındaki dbo.Users tablosuna yeni öğrenci
 * veya öğretim üyesi ekler.
 */
public class RegisterGUI extends JFrame {

	private static final long serialVersionUID = 1L;
	private JTextField usernameField;
	private JPasswordField passwordField;
	private JTextField fullNameField;
	private JComboBox<String> roleCombo;

	public RegisterGUI() {
		setTitle("Kayıt Ol");
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setSize(400, 300);
		setLocationRelativeTo(null);
		initUI();
		setVisible(true);
	}

	private void initUI() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 5, 5, 5);
		gbc.fill = GridBagConstraints.HORIZONTAL;

		// Kullanıcı Adı
		gbc.gridx = 0;
		gbc.gridy = 0;
		panel.add(new JLabel("Kullanıcı Adı:"), gbc);
		usernameField = new JTextField(20);
		gbc.gridx = 1;
		panel.add(usernameField, gbc);

		// Şifre
		gbc.gridx = 0;
		gbc.gridy = 1;
		panel.add(new JLabel("Şifre:"), gbc);
		passwordField = new JPasswordField(20);
		gbc.gridx = 1;
		panel.add(passwordField, gbc);

		// Tam Ad
		gbc.gridx = 0;
		gbc.gridy = 2;
		panel.add(new JLabel("Tam Adı:"), gbc);
		fullNameField = new JTextField(20);
		gbc.gridx = 1;
		panel.add(fullNameField, gbc);

		// Rol
		gbc.gridx = 0;
		gbc.gridy = 3;
		panel.add(new JLabel("Rol:"), gbc);
		roleCombo = new JComboBox<>(new String[] { "Student", "Instructor" });
		gbc.gridx = 1;
		panel.add(roleCombo, gbc);

		// Kayıt Butonu
		JButton registerBtn = new JButton("Kayıt Ol");
		gbc.gridx = 0;
		gbc.gridy = 4;
		gbc.gridwidth = 2;
		panel.add(registerBtn, gbc);
		registerBtn.addActionListener(e -> registerUser());

		add(panel);
	}

	private void registerUser() {
		String username = usernameField.getText().trim();
		String password = new String(passwordField.getPassword()).trim();
		String fullName = fullNameField.getText().trim();
		String role = (String) roleCombo.getSelectedItem();

		// Gerekli alan kontrolü
		if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Lütfen tüm alanları doldurun.", "Uyarı", JOptionPane.WARNING_MESSAGE);
			return;
		}

		String sqlUser = "INSERT INTO dbo.Users (Username, PasswordHash, FullName, Role) VALUES (?, ?, ?, ?)";
		try (Connection conn = DatabaseConnector.connect();
				PreparedStatement psUser = conn.prepareStatement(sqlUser, Statement.RETURN_GENERATED_KEYS)) {
			// 1) Users tablosuna ekle
			psUser.setString(1, username);
			psUser.setString(2, password); // Üretimde hash’leyin
			psUser.setString(3, fullName);
			psUser.setString(4, role);
			psUser.executeUpdate();

			// 2) Yeni eklenen kullanıcı ID'sini al
			try (ResultSet rs = psUser.getGeneratedKeys()) {
				if (rs.next()) {
					int newUserId = rs.getInt(1);

					// 3) Eğer rol Instructor ise, Instructors tablosuna da ekle
					if ("Instructor".equalsIgnoreCase(role)) {
						String sqlInst = "INSERT INTO dbo.Instructors (UserId, Name) VALUES (?, ?)";
						try (PreparedStatement psInst = conn.prepareStatement(sqlInst)) {
							psInst.setInt(1, newUserId);
							psInst.setString(2, fullName);
							psInst.executeUpdate();
						}
					}
				}
			}

			// 4) Başarılı mesaj ve yönlendirme
			JOptionPane.showMessageDialog(this, "Kayıt başarılı! Lütfen giriş yapın.", "Başarılı",
					JOptionPane.INFORMATION_MESSAGE);
			dispose();
			new LoginGUI().setVisible(true);

		} catch (SQLException ex) {
			// Unique constraint (username) hatası
			if (ex.getErrorCode() == 2627) {
				JOptionPane.showMessageDialog(this, "Bu kullanıcı adı zaten kayıtlı.", "Hata",
						JOptionPane.ERROR_MESSAGE);
			} else {
				ex.printStackTrace();
				JOptionPane.showMessageDialog(this, "Kayıt sırasında hata:\n" + ex.getMessage(), "Hata",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(RegisterGUI::new);
	}
}
