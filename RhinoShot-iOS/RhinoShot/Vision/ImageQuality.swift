import Foundation
import CoreVideo
import CoreGraphics

/// Cheap image-quality metrics computed on a small grayscale copy of the frame.
///
///  - brightness : mean luminance of the face crop (0...1)
///  - sharpness  : Tenengrad (mean gradient magnitude) divided by the mean
///                 luminance, which keeps the value comparable between a bright
///                 and a dark clinic.
///
/// This is the iOS twin of the Android `ImageQuality` object and uses the same
/// thresholds, so a clinic can move between platforms without surprises.
enum ImageQuality {

    struct Metrics {
        let brightness: Float
        let sharpness: Float
    }

    /// - Parameters:
    ///   - pixelBuffer: 32BGRA buffer (upright, portrait).
    ///   - box: optional normalised face box; the whole frame is used when nil.
    static func measure(pixelBuffer: CVPixelBuffer, box: CGRect?) -> Metrics {
        let width = CVPixelBufferGetWidth(pixelBuffer)
        let height = CVPixelBufferGetHeight(pixelBuffer)
        guard width > 0, height > 0 else { return Metrics(brightness: 0, sharpness: 0) }

        var crop = CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height))
        if let box = box, box.width > 0, box.height > 0 {
            let x = max(0, min(CGFloat(width) - 1, box.minX * CGFloat(width)))
            let y = max(0, min(CGFloat(height) - 1, box.minY * CGFloat(height)))
            let w = max(4, min(CGFloat(width) - x, box.width * CGFloat(width)))
            let h = max(4, min(CGFloat(height) - y, box.height * CGFloat(height)))
            crop = CGRect(x: x, y: y, width: w, height: h)
        }

        CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }
        guard let base = CVPixelBufferGetBaseAddress(pixelBuffer) else {
            return Metrics(brightness: 0, sharpness: 0)
        }
        let bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)
        let ptr = base.assumingMemoryBound(to: UInt8.self)

        // Down-sample to a small grayscale image (nearest neighbour, fast enough
        // for every frame).
        let targetW = 160
        let targetH = max(24, min(480, Int(Double(targetW) * Double(crop.height / crop.width))))
        var gray = [UInt8](repeating: 0, count: targetW * targetH)

        var sum = 0.0
        for row in 0..<targetH {
            let sy = Int(crop.minY) + row * Int(crop.height) / targetH
            let rowOffset = sy * bytesPerRow
            for col in 0..<targetW {
                let sx = Int(crop.minX) + col * Int(crop.width) / targetW
                let offset = rowOffset + sx * 4
                let b = Double(ptr[offset])
                let g = Double(ptr[offset + 1])
                let r = Double(ptr[offset + 2])
                let value = UInt8(min(255, max(0, (r * 77 + g * 151 + b * 28) / 256)))
                gray[row * targetW + col] = value
                sum += Double(value)
            }
        }

        let mean = sum / Double(targetW * targetH)

        var gradient = 0.0
        var n = 0.0
        if targetW > 2 && targetH > 2 {
            for row in 1..<(targetH - 1) {
                for col in 1..<(targetW - 1) {
                    let i = row * targetW + col
                    let gx = Double(gray[i + 1]) - Double(gray[i - 1])
                    let gy = Double(gray[i + targetW]) - Double(gray[i - targetW])
                    gradient += (gx * gx + gy * gy).squareRoot()
                    n += 1
                }
            }
        }
        let meanGradient = n > 0 ? gradient / n : 0

        return Metrics(
            brightness: Float(mean / 255.0),
            sharpness: Float(meanGradient / (mean + 8.0))
        )
    }
}
