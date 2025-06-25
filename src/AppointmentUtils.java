import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

//Yardımcı sınıf: Randevu zamanlarıyla ilgili işlemleri içerir
public class AppointmentUtils {

	// Verilen zaman aralığında (start - end) ve dolu saatleri dikkate alarak
	// 20 dakikalık boş randevu dilimlerini döndüren metod
	public static List<String> generateAvailableSlots(LocalTime start, LocalTime end, List<LocalTime> bookedTimes) {
		List<String> availableSlots = new ArrayList<>();

		// current, başlangıç saatinden başlar ve 20 dakikalık adımlarla ilerler
		LocalTime current = start;

		// current + 20 dakika, bitiş saatini geçene kadar devam et
		while (!current.plusMinutes(20).isAfter(end)) {
			boolean isBooked = false;

			// Mevcut slot, herhangi bir alınmış randevuyla çakışıyor mu kontrol edilir
			for (LocalTime booked : bookedTimes) {
				LocalTime bookedEnd = booked.plusMinutes(20);

				// Eğer slot tamamen booked'tan önce bitiyorsa VEYA tamamen sonra başlıyorsa →
				// çakışmaz
				// Ancak bu koşul sağlanmıyorsa, çakışma var demektir
				if (!(current.plusMinutes(20).isBefore(booked) || current.isAfter(bookedEnd))) {
					isBooked = true;
					break;
				}
			}

			// Eğer slot boşsa, listeye ekle
			if (!isBooked) {
				availableSlots.add(current + " - " + current.plusMinutes(20));
			}

			// Bir sonraki 20 dakikalık slot'a geç
			current = current.plusMinutes(20);
		}

		// Elde edilen uygun slot listesi döndürülür
		return availableSlots;
	}
}
