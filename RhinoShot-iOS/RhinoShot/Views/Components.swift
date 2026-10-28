import SwiftUI

enum Theme {
    static let ok = Color(red: 0.34, green: 0.79, blue: 0.54)
    static let warn = Color(red: 0.88, green: 0.64, blue: 0.35)
    static let error = Color(red: 0.90, green: 0.45, blue: 0.45)
    static let card = Color(red: 0.086, green: 0.118, blue: 0.141)
    static let muted = Color(red: 0.62, green: 0.69, blue: 0.73)
    static let accent = Color(red: 0.30, green: 0.71, blue: 0.67)
    static let background = Color(red: 0.055, green: 0.078, blue: 0.094)
}

struct PanelCard<Content: View>: View {
    var title: String?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let title = title {
                Text(title)
                    .font(.headline)
                    .foregroundStyle(.white)
            }
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
    }
}

struct CheckRow: View {
    let check: Check

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
                .padding(.top, 5)
            Text(check.message)
                .font(.caption)
                .foregroundStyle(check.ok ? Theme.muted : color)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var color: Color {
        if check.ok { return Theme.ok }
        return check.blocking ? Theme.error : Theme.warn
    }
}

struct MetricBox: View {
    let label: String
    let value: String

    var body: some View {
        VStack(spacing: 2) {
            Text(label).font(.caption2).foregroundStyle(Theme.muted)
            Text(value).font(.system(size: 16, weight: .bold)).foregroundStyle(.white)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
        .background(Color.white.opacity(0.13), in: RoundedRectangle(cornerRadius: 8))
    }
}

/// Simple gauge: the white dot is the current angle, the green dot the target.
struct AngleDial: View {
    let yaw: Float
    let targetYaw: Float?

    var body: some View {
        Canvas { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height * 0.98)
            let radius = size.height * 0.92
            let start: Double = 200
            let sweep: Double = 140

            var arc = Path()
            arc.addArc(
                center: center,
                radius: radius,
                startAngle: .degrees(start),
                endAngle: .degrees(start + sweep),
                clockwise: false
            )
            context.stroke(arc, with: .color(.white.opacity(0.2)), lineWidth: 4)

            func angle(for value: Float) -> Double {
                start + (Double(value.clamped(to: -90...90)) + 90) / 180 * sweep
            }
            func point(_ degrees: Double) -> CGPoint {
                let rad = degrees * .pi / 180
                return CGPoint(x: center.x + radius * CGFloat(cos(rad)),
                               y: center.y + radius * CGFloat(sin(rad)))
            }

            if let target = targetYaw {
                let p = point(angle(for: target))
                context.fill(Path(ellipseIn: CGRect(x: p.x - 10, y: p.y - 10, width: 20, height: 20)),
                             with: .color(Theme.ok))
            }
            let current = point(angle(for: yaw))
            context.fill(Path(ellipseIn: CGRect(x: current.x - 8, y: current.y - 8, width: 16, height: 16)),
                         with: .color(.white))
        }
        .frame(height: 76)
    }
}

extension Float {
    func clamped(to range: ClosedRange<Float>) -> Float {
        Swift.min(range.upperBound, Swift.max(range.lowerBound, self))
    }
}
