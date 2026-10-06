using Microsoft.EntityFrameworkCore;

namespace KnappKiSoftMock.Persistence;

public sealed class AppDbContext(DbContextOptions<AppDbContext> options) : DbContext(options)
{
    public DbSet<PackUnitEntity> PackUnits => Set<PackUnitEntity>();
    public DbSet<MasterdataSessionDeltaEntity> MasterdataDeltas => Set<MasterdataSessionDeltaEntity>();
    public DbSet<InboundDeliveryEntity> InboundDeliveries => Set<InboundDeliveryEntity>();
    public DbSet<InboundDeliveryProgressEntity> InboundProgress => Set<InboundDeliveryProgressEntity>();
    public DbSet<ToteCompartmentEntity> ToteCompartments => Set<ToteCompartmentEntity>();
    public DbSet<AsrsStockEntity> AsrsStock => Set<AsrsStockEntity>();
    public DbSet<GoodsOutOrderEntity> GoodsOutOrders => Set<GoodsOutOrderEntity>();
    public DbSet<InventoryRequestEntity> InventoryRequests => Set<InventoryRequestEntity>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<PackUnitEntity>(e =>
        {
            e.HasIndex(x => new { x.ClientNumber, x.ArticleNumber, x.PackSize }).IsUnique();
        });
        modelBuilder.Entity<MasterdataSessionDeltaEntity>(e =>
        {
            e.HasIndex(x => new { x.Domain, x.ClientNumber, x.KeyValue }).IsUnique();
            e.Property(x => x.KeyValue).HasMaxLength(256);
        });
        modelBuilder.Entity<InboundDeliveryEntity>(e =>
        {
            e.HasIndex(x => new { x.ClientNumber, x.InboundDeliveryNumber }).IsUnique();
        });
        modelBuilder.Entity<InboundDeliveryProgressEntity>(e =>
        {
            e.HasIndex(x => new { x.ClientNumber, x.InboundDeliveryNumber, x.LineReference }).IsUnique();
            e.Ignore(x => x.OpenQuantity);
        });
        modelBuilder.Entity<ToteCompartmentEntity>(e =>
        {
            e.HasIndex(x => new { x.ClientNumber, x.LoadUnitCode, x.Compartment }).IsUnique();
        });
        modelBuilder.Entity<AsrsStockEntity>(e =>
        {
            e.HasIndex(x => new { x.ClientNumber, x.ArticleNumber, x.PackSize, x.ReservationCode }).IsUnique();
            e.Property(x => x.ReservationCode).HasDefaultValue("");
        });
        modelBuilder.Entity<GoodsOutOrderEntity>(e =>
        {
            e.HasIndex(x => new { x.ClientNumber, x.OrderNumber, x.SheetNumber }).IsUnique();
        });
        modelBuilder.Entity<InventoryRequestEntity>(e =>
        {
            e.HasIndex(x => new { x.ClientNumber, x.RequestNumber }).IsUnique();
        });
    }
}
